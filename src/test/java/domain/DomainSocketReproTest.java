package domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.google.protobuf.Any;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import test.TestItGrpc;
import test.TestItGrpc.TestItImplBase;

public class DomainSocketReproTest {

    public static class TestServer extends TestItImplBase {

        @Override
        public void ping(Any request, StreamObserver<Any> responseObserver) {
            responseObserver.onNext(Any.getDefaultInstance());
            responseObserver.onCompleted();
        }

    }

    @Test
    public void smokin() throws Exception {
        Path socketPath = Path.of("target").resolve("smokin.socket");
        Files.deleteIfExists(socketPath);
        assertFalse(Files.exists(socketPath));

        var group = new KQueueEventLoopGroup();
        var server = NettyServerBuilder.forAddress(new DomainSocketAddress(socketPath.toFile()))
                                       .channelType(KQueueServerDomainSocketChannel.class)
                                       .workerEventLoopGroup(group)
                                       .bossEventLoopGroup(group)
                                       .addService(new TestServer())
                                       .build();
        server.start();
        assertTrue(Files.exists(socketPath));

        ManagedChannel channel = NettyChannelBuilder.forAddress(new DomainSocketAddress(socketPath.toFile()))
                                                    .eventLoopGroup(new KQueueEventLoopGroup())
                                                    .channelType(KQueueSocketChannel.class)
                                                    .keepAliveTime(1, TimeUnit.MILLISECONDS)
                                                    .usePlaintext()
                                                    .build();
        var stub = TestItGrpc.newBlockingStub(channel);

        var result = stub.ping(Any.newBuilder().build());
        assertNotNull(result);

        System.out.println("Success!" + result);
    }

}
