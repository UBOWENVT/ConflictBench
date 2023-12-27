package org.jctools.queues.atomic;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueue.Supplier;
import org.jctools.queues.MessagePassingQueueUtil;
import org.jctools.queues.QueueProgressIndicators;
import org.jctools.queues.IndexedQueueSizeUtil;
import static org.jctools.queues.atomic.LinkedAtomicArrayQueueUtil.*;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.jctools.queues.MpmcArrayQueue;

/**
 * This is a weakened version of the MPSC algorithm as presented <a
 * href="http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue"> on 1024
 * Cores</a> by D. Vyukov. The original has been adapted to Java and it's quirks with regards to memory model and
 * layout:
 * <ol>
 * <li>As this is an SPSC we have no need for XCHG, an ordered store is enough.
 * </ol>
 * The queue is initialized with a stub node which is set to both the producer and consumer node references. From this
 * point follow the notes on offer/poll.
 *
 * @author nitsanw
 *
 * @param <E>
 */
public class SpscLinkedAtomicQueue<E extends java.lang.Object> extends BaseLinkedAtomicQueue<E> {
  public SpscLinkedAtomicQueue() {
    LinkedQueueAtomicNode<E> node = newNode();
    spProducerNode(node);
    spConsumerNode(node);
    node.soNext(null);
  }

  /**
     * {@inheritDoc} <br>
     *
     * IMPLEMENTATION NOTES:<br>
     * Offer is allowed from a SINGLE thread.<br>
     * Offer allocates a new node (holding the offered value) and:
     * <ol>
     * <li>Sets that node as the producerNode.next
     * <li>Sets the new node as the producerNode
     * </ol>
     * From this follows that producerNode.next is always null and for all other nodes node.next is not null.
     *
     * @see MessagePassingQueue#offer(Object)
     * @see java.util.Queue#offer(java.lang.Object)
     */
  @Override public boolean offer(final E e) {
    if (null == e) {
      throw new NullPointerException();
    }
    final LinkedQueueAtomicNode<E> nextNode = newNode(e);
    lpProducerNode().soNext(nextNode);
    spProducerNode(nextNode);
    return true;
  }

  /**
     * {@inheritDoc} <br>
     *
     * IMPLEMENTATION NOTES:<br>
     * Poll is allowed from a SINGLE thread.<br>
     * Poll reads the next node from the consumerNode and:
     * <ol>
     * <li>If it is null, the queue is empty.
     * <li>If it is not null set it as the consumer node and return it's now evacuated value.
     * </ol>
     * This means the consumerNode.value is always null, which is also the starting point for the queue. Because null
     * values are not allowed to be offered this is the only node with it's value set to null at any one time.
     *
     */
  @Override public E poll() {
    return relaxedPoll();
  }

  @Override public E peek() {
    return relaxedPeek();
  }

  @Override public int fill(Supplier<E> s) {
    long result = 0;
    do {
      fill(s, 4096);
      result += 4096;
    } while(result <= Integer.MAX_VALUE - 4096);
    return (int) result;
  }

  @Override public int fill(Supplier<E> s, int limit) {
    if (limit == 0) {
      return 0;
    }
    LinkedQueueAtomicNode<E> tail = newNode(s.get());
    final LinkedQueueAtomicNode<E> head = tail;
    for (int i = 1; i < limit; i++) {
      final LinkedQueueAtomicNode<E> temp = newNode(s.get());
      tail.soNext(temp);
      tail = temp;
    }
    final LinkedQueueAtomicNode<E> oldPNode = lpProducerNode();
    oldPNode.soNext(head);
    spProducerNode(tail);
    return limit;
  }

  @Override public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit) {
    LinkedQueueAtomicNode<E> chaserNode = producerNode;
    while (exit.keepRunning()) {
      for (int i = 0; i < 4096; i++) {
        final LinkedQueueAtomicNode<E> nextNode = newNode(s.get());
        chaserNode.soNext(nextNode);
        chaserNode = nextNode;
        this.producerNode = chaserNode;
      }
    }
  }
}