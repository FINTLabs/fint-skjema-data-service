package no.fintlabs.kafka.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.requestreply.RequestReplyFuture;
import org.springframework.kafka.support.SendResult;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

@Slf4j
public class FintKafkaRequestReplyUtil {

    // TODO: 22/11/2021 Move mapper
    public static final ObjectMapper mapper = new ObjectMapper();

    private FintKafkaRequestReplyUtil() {
    }

    public static <T1, T2> Tuple2<T1, T2> getParallel(
            RequestReplyOperationArgs<T1> requestReyplyOperationArgs1,
            RequestReplyOperationArgs<T2> requestReyplyOperationArgs2
    ) {
        Object[] valuesInSpecifiedOrder = getValuesInSpecifiedOrder(
                requestReyplyOperationArgs1,
                requestReyplyOperationArgs2
        );
        return Tuples.<T1, T2>fn2().apply(valuesInSpecifiedOrder);
    }

    public static <T1, T2, T3> Tuple3<T1, T2, T3> getParallel(
            RequestReplyOperationArgs<T1> requestReyplyOperationArgs1,
            RequestReplyOperationArgs<T2> requestReyplyOperationArgs2,
            RequestReplyOperationArgs<T3> requestReyplyOperationArgs3
    ) {
        Object[] valuesInSpecifiedOrder = getValuesInSpecifiedOrder(
                requestReyplyOperationArgs1,
                requestReyplyOperationArgs2,
                requestReyplyOperationArgs3
        );
        return Tuples.<T1, T2, T3>fn3().apply(valuesInSpecifiedOrder);
    }

    public static Object[] getValuesInSpecifiedOrder(RequestReplyOperationArgs<?>... args) {
        Object[] values = new Object[args.length];
        AsyncGetHandler asyncGetHandler = new AsyncGetHandler(args.length);
        IntStream.range(0, args.length).forEach(index ->
                FintKafkaRequestReplyUtil.getAsync(new RequestReyplyAsyncOperationArgs<>(
                                args[index],
                                value -> {
                                    values[index] = value;
                                    asyncGetHandler.successCallback();
                                },
                                ex -> {
                                    return; // TODO: 23/11/2021
                                }
                        )
                ));
        synchronized (asyncGetHandler.syncObject) {
            try {
                asyncGetHandler.syncObject.wait();
            } catch (InterruptedException e) {
                // TODO: 23/11/2021
            }
        }
        return values;
    }

    public static <T> void getAsync(RequestReyplyAsyncOperationArgs<T> requestReyplyAsyncOperationArgs) {
        RequestReplyFuture<String, Object, String> replyFuture = FintKafkaRequestReplyUtil.send(
                requestReyplyAsyncOperationArgs.requestTopic,
                requestReyplyAsyncOperationArgs.requestValue,
                requestReyplyAsyncOperationArgs.replyingKafkaTemplate
        );
        if (replyFuture == null) {
            // TODO: 23/11/2021 Handle with exception
            return;
        }
        replyFuture.addCallback(
                result -> {
                    try {
                        if (result == null) {
                            // TODO: 23/11/2021 Handle with exception
                            return;
                        }
                        T value = FintKafkaRequestReplyUtil.mapper.readValue(
                                result.value(),
                                requestReyplyAsyncOperationArgs.replyValueClass
                        );
                        log.info("Return value: " + result.value());
                        requestReyplyAsyncOperationArgs.successCallback.accept(value);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                },
                requestReyplyAsyncOperationArgs.failureCallback::accept
        );
    }

    public static <T> T get(RequestReplyOperationArgs<T> requestReplyOperationArgs) {
        RequestReplyFuture<String, Object, String> replyFuture = FintKafkaRequestReplyUtil.send(
                requestReplyOperationArgs.requestTopic,
                requestReplyOperationArgs.requestValue,
                requestReplyOperationArgs.replyingKafkaTemplate
        );
        if (replyFuture == null) {
            return null; // TODO: 23/11/2021 Handle with exception
        }
        try {
            ConsumerRecord<String, String> consumerRecord = replyFuture.get();
            log.info("Return value: " + consumerRecord.value());
            return FintKafkaRequestReplyUtil.mapper.readValue(consumerRecord.value(), requestReplyOperationArgs.replyValueClass);
        } catch (InterruptedException | ExecutionException | JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static RequestReplyFuture<String, Object, String> send(
            String requestTopic,
            Object requestValue,
            ReplyingKafkaTemplate<String, Object, String> replyingKafkaTemplate
    ) {
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(requestTopic, requestValue);
        RequestReplyFuture<String, Object, String> replyFuture = replyingKafkaTemplate.sendAndReceive(producerRecord);
        try {
            SendResult<String, Object> sendResult = replyFuture.getSendFuture().get();
            log.info("Sent ok: " + sendResult.getRecordMetadata());
            return replyFuture;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

}
