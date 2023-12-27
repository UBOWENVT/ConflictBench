package com.codingapi.tx.spi.message.netty.impl; 

import com.codingapi.tx.spi.message.RpcClientInitializer; 
import com.codingapi.tx.spi.message.dto.TxManagerHost; 
import com.codingapi.tx.spi.message.netty.SocketManager; 
import com.codingapi.tx.spi.message.netty.em.NettyType; 
import com.codingapi.tx.spi.message.netty.handler.NettyRpcClientHandlerInitHandler; 
import io.netty.bootstrap.Bootstrap; 
import io.netty.channel.ChannelFuture; 
import io.netty.channel.ChannelOption; 
import io.netty.channel.EventLoopGroup; 
import io.netty.channel.nio.NioEventLoopGroup; 
import io.netty.channel.socket.nio.NioSocketChannel; 
import lombok.extern.slf4j.Slf4j; 
import org.springframework.beans.factory.DisposableBean; 
import org.springframework.beans.factory.annotation.Autowired; 
import org.springframework.stereotype.Service; 

import java.net.ConnectException; 

import java.net.InetSocketAddress; 
import java.net.SocketAddress; 
import java.util.List; 
import com.codingapi.tx.spi.message.RpcConfig; 
import java.util.concurrent.TimeUnit; 

/**
 * Description:
 * Company: CodingApi
 * Date: 2018/12/10
 *
 * @author ujued
 */
  class  NettyRpcClientInitializer   {
	


    

	

    

	


    <<<<<<< /home/ppp/Research_Projects/Merge_Conflicts/Resource/workspace/result/merge/fstmerge_tmp1647924605664/fstmerge_var1_1995276554068275195
@Override
    public void init(List<TxManagerHost> hosts) {
        NettyContext.type = NettyType.client;
        NettyContext.params = hosts;
        workerGroup = new NioEventLoopGroup();
        for (TxManagerHost host : hosts) {
            connect(new InetSocketAddress(host.getHost(), host.getPort()));
        }
    }
=======
>>>>>>> /home/ppp/Research_Projects/Merge_Conflicts/Resource/workspace/result/merge/fstmerge_tmp1647924605664/fstmerge_var2_6678130190680217908


	


    <<<<<<< /home/ppp/Research_Projects/Merge_Conflicts/Resource/workspace/result/merge/fstmerge_tmp1647924605714/fstmerge_var1_1251811473803789006
@Override
    public synchronized void connect(SocketAddress socketAddress) {
        int reconnect = rpcConfig.getReconnectCount();
        while (true) {
            if (SocketManager.getInstance().noConnect(socketAddress) && reconnect >= 0) {
                try {
                    log.info("try reconnect {} [{}]", socketAddress, rpcConfig.getReconnectCount() - reconnect + 1);
                    Bootstrap b = new Bootstrap();
                    b.group(workerGroup);
                    b.channel(NioSocketChannel.class);
                    b.option(ChannelOption.SO_KEEPALIVE, true);
                    b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
                    b.handler(nettyRpcClientHandlerInitHandler);
                    ChannelFuture channelFuture = b.connect(socketAddress).syncUninterruptibly();
                    log.info("client -> {}, state:{}", socketAddress, channelFuture.isSuccess());
                    break;
                } catch (Exception ignored) {
                    reconnect--;
                    try {
                        TimeUnit.SECONDS.sleep(rpcConfig.getAttrDelayTime());
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }
    }
=======
>>>>>>> /home/ppp/Research_Projects/Merge_Conflicts/Resource/workspace/result/merge/fstmerge_tmp1647924605714/fstmerge_var2_6471238628873838037


	


    <<<<<<< /home/ppp/Research_Projects/Merge_Conflicts/Resource/workspace/result/merge/fstmerge_tmp1647924605716/fstmerge_var1_1080024575332937718
@Override
    public void destroy() {
        workerGroup.shutdownGracefully();
        log.info("client was down.");
    }
=======
>>>>>>> /home/ppp/Research_Projects/Merge_Conflicts/Resource/workspace/result/merge/fstmerge_tmp1647924605716/fstmerge_var2_7157380626769782130


	

    @Autowired
    private RpcConfig rpcConfig;


}
