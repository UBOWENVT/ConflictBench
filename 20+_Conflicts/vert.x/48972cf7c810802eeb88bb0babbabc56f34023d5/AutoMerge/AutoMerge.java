package org.vertx.java.core.eventbus.impl;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.impl.hazelcast.HazelcastClusterManager;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.net.impl.ServerID;
import org.vertx.java.core.parsetools.RecordParser;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultEventBus implements EventBus {
  private static final Logger log = LoggerFactory.getLogger(DefaultEventBus.class);

  private static final Buffer PONG = new Buffer(new byte[] { (byte) 1 });

  private static final long PING_INTERVAL = 20000;

  private static final long PING_REPLY_INTERVAL = 20000;

  public static final int DEFAULT_CLUSTER_PORT = 2550;

  private final VertxInternal vertx;

  private final ServerID serverID;

  private NetServer server;

  private SubsMap subs;

  private final ConcurrentMap<ServerID, ConnectionHolder> connections = new ConcurrentHashMap<>();

  private final ConcurrentMap<String, Map<HandlerHolder, String>> handlers = new ConcurrentHashMap<>();

  private final Map<String, HandlerInfo> handlersByID = new ConcurrentHashMap<>();

  public void dump() {
    System.out.println("Handlers: " + this.handlers.size());
    System.out.println("Connections: " + this.connections.size());
    System.out.println("Handlers by id: " + this.handlersByID.size());
  }

  public DefaultEventBus(VertxInternal vertx) {
    this.vertx = vertx;
    this.serverID = new ServerID(DEFAULT_CLUSTER_PORT, "localhost");
    this.server = null;
    this.subs = null;
  }

  public DefaultEventBus(VertxInternal vertx, String hostname) {
    this(vertx, DEFAULT_CLUSTER_PORT, hostname);
  }

  public DefaultEventBus(VertxInternal vertx, int port, String hostname) {
    this.vertx = vertx;
    this.serverID = new ServerID(port, hostname);
    ClusterManager mgr = new HazelcastClusterManager(vertx);
    subs = mgr.getSubsMap("subs");
    this.server = setServer();
  }

  public void send(String address, JsonObject message, final Handler<Message<JsonObject>> replyHandler) {
    sendOrPub(true, new JsonMessage(address, message), replyHandler);
  }

  public void send(String address, JsonObject message) {
    send(address, message, null);
  }

  public void send(String address, Buffer message, final Handler<Message<Buffer>> replyHandler) {
    sendOrPub(true, new BufferMessage(address, message), replyHandler);
  }

  public void send(String address, Buffer message) {
    send(address, message, null);
  }

  public void send(String address, byte[] message, final Handler<Message<byte[]>> replyHandler) {
    sendOrPub(true, new ByteArrayMessage(address, message), replyHandler);
  }

  public void send(String address, byte[] message) {
    send(address, message, null);
  }

  public void send(String address, String message, final Handler<Message<String>> replyHandler) {
    sendOrPub(true, new StringMessage(address, message), replyHandler);
  }

  public void send(String address, String message) {
    send(address, message, null);
  }

  public void send(String address, Integer message, final Handler<Message<Integer>> replyHandler) {
    sendOrPub(true, new IntMessage(address, message), replyHandler);
  }

  public void send(String address, Integer message) {
    send(address, message, null);
  }

  public void send(String address, Long message, final Handler<Message<Long>> replyHandler) {
    sendOrPub(true, new LongMessage(address, message), replyHandler);
  }

  public void send(String address, Long message) {
    send(address, message, null);
  }

  public void send(String address, Float message, final Handler<Message<Float>> replyHandler) {
    sendOrPub(true, new FloatMessage(address, message), replyHandler);
  }

  public void send(String address, Float message) {
    send(address, message, null);
  }

  public void send(String address, Double message, final Handler<Message<Double>> replyHandler) {
    sendOrPub(true, new DoubleMessage(address, message), replyHandler);
  }

  public void send(String address, Double message) {
    send(address, message, null);
  }

  public void send(String address, Boolean message, final Handler<Message<Boolean>> replyHandler) {
    sendOrPub(true, new BooleanMessage(address, message), replyHandler);
  }

  public void send(String address, Boolean message) {
    send(address, message, null);
  }

  public void send(String address, Short message, final Handler<Message<Short>> replyHandler) {
    sendOrPub(true, new ShortMessage(address, message), replyHandler);
  }

  public void send(String address, Short message) {
    send(address, message, null);
  }

  public void send(String address, Character message, final Handler<Message<Character>> replyHandler) {
    sendOrPub(true, new CharacterMessage(address, message), replyHandler);
  }

  public void send(String address, Character message) {
    send(address, message, null);
  }

  public void send(String address, Byte message, final Handler<Message<Byte>> replyHandler) {
    sendOrPub(true, new ByteMessage(address, message), replyHandler);
  }

  public void send(String address, Byte message) {
    send(address, message, null);
  }

  public void publish(String address, JsonObject message, final Handler<Message<JsonObject>> replyHandler) {
    sendOrPub(true, new JsonMessage(address, message), replyHandler);
  }

  public void publish(String address, JsonObject message) {
    publish(address, message, null);
  }

  public void publish(String address, Buffer message, final Handler<Message<Buffer>> replyHandler) {
    sendOrPub(false, new BufferMessage(address, message), replyHandler);
  }

  public void publish(String address, Buffer message) {
    publish(address, message, null);
  }

  public void publish(String address, byte[] message, final Handler<Message<byte[]>> replyHandler) {
    sendOrPub(false, new ByteArrayMessage(address, message), replyHandler);
  }

  public void publish(String address, byte[] message) {
    publish(address, message, null);
  }

  public void publish(String address, String message, final Handler<Message<String>> replyHandler) {
    sendOrPub(false, new StringMessage(address, message), replyHandler);
  }

  public void publish(String address, String message) {
    publish(address, message, null);
  }

  public void publish(String address, Integer message, final Handler<Message<Integer>> replyHandler) {
    sendOrPub(false, new IntMessage(address, message), replyHandler);
  }

  public void publish(String address, Integer message) {
    publish(address, message, null);
  }

  public void publish(String address, Long message, final Handler<Message<Long>> replyHandler) {
    sendOrPub(false, new LongMessage(address, message), replyHandler);
  }

  public void publish(String address, Long message) {
    publish(address, message, null);
  }

  public void publish(String address, Float message, final Handler<Message<Float>> replyHandler) {
    sendOrPub(false, new FloatMessage(address, message), replyHandler);
  }

  public void publish(String address, Float message) {
    publish(address, message, null);
  }

  public void publish(String address, Double message, final Handler<Message<Double>> replyHandler) {
    sendOrPub(false, new DoubleMessage(address, message), replyHandler);
  }

  public void publish(String address, Double message) {
    publish(address, message, null);
  }

  public void publish(String address, Boolean message, final Handler<Message<Boolean>> replyHandler) {
    sendOrPub(false, new BooleanMessage(address, message), replyHandler);
  }

  public void publish(String address, Boolean message) {
    publish(address, message, null);
  }

  public void publish(String address, Short message, final Handler<Message<Short>> replyHandler) {
    sendOrPub(false, new ShortMessage(address, message), replyHandler);
  }

  public void publish(String address, Short message) {
    publish(address, message, null);
  }

  public void publish(String address, Character message, final Handler<Message<Character>> replyHandler) {
    sendOrPub(false, new CharacterMessage(address, message), replyHandler);
  }

  public void publish(String address, Character message) {
    publish(address, message, null);
  }

  public void publish(String address, Byte message, final Handler<Message<Byte>> replyHandler) {
    sendOrPub(false, new ByteMessage(address, message), replyHandler);
  }

  public void publish(String address, Byte message) {
    publish(address, message, null);
  }

  public void unregisterHandler(String address, Handler<? extends Message> handler, AsyncResultHandler<Void> completionHandler) {
    Context context = vertx.getOrAssignContext();
    Map<HandlerHolder, String> map = handlers.get(address);
    if (map != null) {
      String handlerID = map.remove(new HandlerHolder(handler, false, context));
      if (handlerID != null) {
        handlersByID.remove(handlerID);
      }
      if (map.isEmpty()) {
        handlers.remove(address);
        if (subs != null) {
          removeSub(address, serverID, completionHandler);
        } else {
          if (completionHandler != null) {
            callCompletionHandler(completionHandler);
          }
        }
      } else {
        if (completionHandler != null) {
          callCompletionHandler(completionHandler);
        }
      }
    }
  }

  public void unregisterHandler(String address, Handler<? extends Message> handler) {
    unregisterHandler(address, handler, null);
  }

  public void unregisterHandler(String id) {
    unregisterHandler(id, (AsyncResultHandler<Void>) null);
  }

  public void unregisterHandler(String id, AsyncResultHandler<Void> completionHandler) {
    HandlerInfo info = handlersByID.get(id);
    if (info != null) {
      unregisterHandler(info.address, info.handler, completionHandler);
    }
  }

  public String registerHandler(Handler<? extends Message> handler) {
    return registerHandler(handler, null);
  }

  public String registerHandler(Handler<? extends Message> handler, AsyncResultHandler<Void> completionHandler) {
    return registerHandler(null, handler, completionHandler, false, false);
  }

  public String registerHandler(String address, Handler<? extends Message> handler, AsyncResultHandler<Void> completionHandler) {
    return registerHandler(address, handler, completionHandler, false, false);
  }

  public String registerHandler(String address, Handler<? extends Message> handler) {
    return registerHandler(address, handler, null);
  }

  public String registerLocalHandler(String address, Handler<? extends Message> handler) {
    return registerHandler(address, handler, null, false, true);
  }

  public String registerLocalHandler(Handler<? extends Message> handler) {
    return registerHandler(null, handler, null, false, true);
  }

  public void close(Handler<Void> doneHandler) {
    server.close(doneHandler);
  }

  void sendReply(final ServerID dest, final BaseMessage message, final Handler replyHandler) {
    sendOrPub(false, dest, message, replyHandler);
  }

  private NetServer setServer() {
    return vertx.createNetServer().connectHandler(new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        final RecordParser parser = RecordParser.newFixed(4, null);
        Handler<Buffer> handler = new Handler<Buffer>() {
          int size = -1;

          public void handle(Buffer buff) {
            if (size == -1) {
              size = buff.getInt(0);
              parser.fixedSizeMode(size);
            } else {
              BaseMessage received = MessageFactory.read(buff);
              if (received.type() == MessageFactory.TYPE_PING) {
                socket.write(PONG);
              } else {
                receiveMessage(received);
              }
              parser.fixedSizeMode(4);
              size = -1;
            }
          }
        };
        parser.setOutput(handler);
        socket.dataHandler(parser);
      }
    }).listen(serverID.port, serverID.host);
  }

  private void sendToSubs(Collection<ServerID> subs, BaseMessage message) {
    for (ServerID serverID : subs) {
      if (!serverID.equals(DefaultEventBus.this.serverID)) {
        sendRemote(serverID, message);
      }
    }
  }

  private void sendOrPub(boolean send, final BaseMessage message, final Handler replyHandler) {
    sendOrPub(send, null, message, replyHandler);
  }

  private void sendOrPub(boolean send, ServerID replyDest, final BaseMessage message, final Handler replyHandler) {
    Context context = vertx.getOrAssignContext();
    try {
      message.sender = serverID;
      if (replyHandler != null) {
        message.replyAddress = UUID.randomUUID().toString();
        registerHandler(message.replyAddress, replyHandler, null, true, false);
      }
      if (replyDest != null) {
        if (!replyDest.equals(this.serverID)) {
          sendRemote(replyDest, message);
        } else {
          receiveMessage(message);
        }
      } else {
        if (subs != null) {
          subs.get(message.address, new AsyncResultHandler<Collection<ServerID>>() {
            public void handle(AsyncResult<Collection<ServerID>> event) {
              if (event.exception == null) {
                Collection<ServerID> serverIDs = event.result;
                if (serverIDs != null) {
                  sendToSubs(serverIDs, message);
                }
              } else {
                log.error("Failed to send message", event.exception);
              }
            }
          });
        }
        receiveMessage(message);
      }
    }  finally {
      if (context != null) {
        Context.setContext(context);
      }
    }
  }

  private String registerHandler(String address, Handler<? extends Message> handler, AsyncResultHandler<Void> completionHandler, boolean replyHandler, boolean localOnly) {
    Context context = vertx.getOrAssignContext();
    final String id = UUID.randomUUID().toString();
    if (address == null) {
      address = id;
    }
    handlersByID.put(id, new HandlerInfo(address, handler));
    Map<HandlerHolder, String> map = handlers.get(address);
    if (map == null) {
      map = new ConcurrentHashMap<>();
      Map<HandlerHolder, String> prevMap = handlers.putIfAbsent(address, map);
      if (prevMap != null) {
        map = prevMap;
      }
      if (completionHandler == null) {
        completionHandler = new AsyncResultHandler<Void>() {
          public void handle(AsyncResult<Void> event) {
            if (event.exception != null) {
              log.error("Failed to remove entry", event.exception);
            }
          }
        };
      }
      map.put(new HandlerHolder(handler, replyHandler, context), id);
      if (subs != null && !replyHandler && !localOnly) {
        subs.put(address, serverID, completionHandler);
      } else {
        if (completionHandler != null) {
          callCompletionHandler(completionHandler);
        }
      }
    } else {
      map.put(new HandlerHolder(handler, replyHandler, context), id);
      if (completionHandler != null) {
        callCompletionHandler(completionHandler);
      }
    }
    context.addCloseHook(new Runnable() {
      public void run() {
        unregisterHandler(id);
      }
    });
    return id;
  }

  private void callCompletionHandler(AsyncResultHandler<Void> completionHandler) {
    AsyncResult<Void> f = new AsyncResult<>((Void) null);
    completionHandler.handle(f);
  }

  private void cleanupConnection(String address, ServerID serverID, ConnectionHolder holder) {
    if (holder.timeoutID != -1) {
      vertx.cancelTimer(holder.timeoutID);
    }
    if (holder.pingTimeoutID != -1) {
      vertx.cancelTimer(holder.pingTimeoutID);
    }
    try {
      holder.socket.close();
    } catch (Exception ignore) {
    }
    if (connections.remove(serverID, holder)) {
      connections.remove(serverID);
      log.debug("Cluster connection closed: " + serverID + " holder " + holder);
      if (subs != null) {
        removeSub(address, serverID, null);
      }
    }
  }

  private void sendRemote(final ServerID serverID, final BaseMessage message) {
    ConnectionHolder holder = connections.get(serverID);
    if (holder == null) {
      NetClient client = vertx.createNetClient();
      client.setConnectTimeout(60 * 1000);
      holder = new ConnectionHolder(client);
      ConnectionHolder prevHolder = connections.putIfAbsent(serverID, holder);
      if (prevHolder != null) {
        prevHolder = holder;
      } else {
        holder.connect(client, serverID, message.address);
      }
    }
    holder.writeMessage(message);
  }

  private void schedulePing(final String address, final ConnectionHolder holder) {
    holder.pingTimeoutID = vertx.setTimer(PING_INTERVAL, new Handler<Long>() {
      public void handle(Long ignore) {
        holder.timeoutID = vertx.setTimer(PING_REPLY_INTERVAL, new Handler<Long>() {
          public void handle(Long timerID) {
            log.info("No pong from server " + serverID + " - will consider it dead, timerID: " + timerID + " holder " + holder);
            cleanupConnection(address, serverID, holder);
          }
        });
        new PingMessage(serverID).write(holder.socket);
      }
    });
  }

  private void removeSub(String subName, ServerID serverID, final AsyncResultHandler<Void> completionHandler) {
    subs.remove(subName, serverID, new AsyncResultHandler<Boolean>() {
      public void handle(AsyncResult<Boolean> event) {
        if (completionHandler != null) {
          AsyncResult<Void> result;
          if (event.exception != null) {
            result = new AsyncResult<Void>(event.exception);
          } else {
            result = new AsyncResult<Void>((Void) null);
          }
          completionHandler.handle(result);
        } else {
          if (event.exception != null) {
            log.error("Failed to remove subscription", event.exception);
          }
        }
      }
    });
  }

  private void receiveMessage(BaseMessage msg) {
    msg.bus = this;
    final Map<HandlerHolder, String> map = handlers.get(msg.address);
    if (map != null) {
      for (final HandlerHolder holder : map.keySet()) {
        final Message copied = msg.copy();
        holder.context.execute(new Runnable() {
          public void run() {
            if (map.containsKey(holder)) {
              try {
                holder.handler.handle(copied);
              }  finally {
                if (holder.replyHandler) {
                  unregisterHandler(msg.address, holder.handler);
                }
              }
            }
          }
        });
      }
    }
  }


<<<<<<< /home/ppp/Research_Projects/Merge_Conflicts/Resource/Paper/bowen-icsme-2023-data/ConflictBench/20_Conflicts/workspace/left/src/main/java/org/vertx/java/core/eventbus/impl/DefaultEventBus.java
  private void doReceive(final Handlers handlers, BaseMessage msg, final HandlerHolder holder) {
    final Message copied = msg.copy();
    holder.context.execute(new Runnable() {
      public void run() {
        if (handlers.map.containsKey(holder)) {
          holder.handler.handle(copied);
        }
      }
    });
  }
=======
>>>>>>> Unknown file: This is a bug in JDime.


  private static class HandlerHolder {
    final Context context;

    final Handler handler;

    final boolean replyHandler;

    HandlerHolder(Handler handler, boolean replyHandler, Context context) {
      this.context = context;
      this.handler = handler;
      this.replyHandler = replyHandler;
    }

    @Override public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      HandlerHolder that = (HandlerHolder) o;
      return handler.equals(that.handler);
    }

    @Override public int hashCode() {
      return handler.hashCode();
    }
  }

  private class ConnectionHolder {
    final NetClient client;

    volatile NetSocket socket;

    final Queue<BaseMessage> pending = new ConcurrentLinkedQueue<>();

    volatile boolean connected;

    long timeoutID = -1;

    long pingTimeoutID = -1;

    private ConnectionHolder(NetClient client) {
      this.client = client;
    }

    void writeMessage(BaseMessage message) {
      if (connected) {
        message.write(socket);
      } else {
        synchronized (this) {
          if (connected) {
            message.write(socket);
          } else {
            pending.add(message);
          }
        }
      }
    }

    synchronized void connected(NetSocket socket, final String address) {
      this.socket = socket;
      connected = true;
      socket.exceptionHandler(new Handler<Exception>() {
        public void handle(Exception e) {
          cleanupConnection(address, serverID, ConnectionHolder.this);
        }
      });
      socket.closedHandler(new SimpleHandler() {
        public void handle() {
          cleanupConnection(address, serverID, ConnectionHolder.this);
        }
      });
      socket.dataHandler(new Handler<Buffer>() {
        public void handle(Buffer data) {
          vertx.cancelTimer(timeoutID);
          schedulePing(address, ConnectionHolder.this);
        }
      });
      schedulePing(address, ConnectionHolder.this);
      for (BaseMessage message : pending) {
        message.write(socket);
      }
      pending.clear();
    }

    void connect(NetClient client, final ServerID serverID, final String address) {
      client.connect(serverID.port, serverID.host, new Handler<NetSocket>() {
        public void handle(final NetSocket socket) {
          connected(socket, address);
        }
      });
      client.exceptionHandler(new Handler<Exception>() {
        public void handle(Exception e) {
          cleanupConnection(address, serverID, ConnectionHolder.this);
        }
      });
    }
  }

  private class HandlerCloseHook implements Runnable {
    final List<String> ids = new ArrayList<>();

    public void run() {
      for (String id : ids) {
        unregisterHandler(id);
      }
    }
  }

  private static class HandlerInfo {
    final String address;

    final Handler<? extends Message> handler;

    private HandlerInfo(String address, Handler<? extends Message> handler) {
      this.address = address;
      this.handler = handler;
    }
  }
}