package com.example.demo.aggregating;

import com.example.demo.UuidColumnRangePartitioner;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.message.Message;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.integration.config.annotation.EnableBatchIntegration;
import org.springframework.batch.integration.partition.RemotePartitioningWorkerStepBuilderFactory;
import org.springframework.batch.integration.partition.StepExecutionRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.aws.inbound.SqsMessageDrivenChannelAdapter;
import org.springframework.integration.aws.outbound.SqsMessageHandler;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.json.JsonToObjectTransformer;
import org.springframework.integration.json.ObjectToJsonTransformer;
import org.springframework.integration.transformer.Transformer;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import org.springframework.integration.transformer.Transformer;

import static com.example.demo.aggregating.ManagerConfiguration.REPLY_QUEUE;
import static com.example.demo.aggregating.ManagerConfiguration.REQUEST_QUEUE;

@Configuration
@EnableBatchProcessing
@EnableBatchIntegration
@Profile("worker")
@RequiredArgsConstructor
public class WorkerConfiguration {

    private final JobRepository jobRepository; // Job repository for Spring Batch

    private final RemotePartitioningWorkerStepBuilderFactory workerStepBuilderFactory;

    private final SqsAsyncClient sqsAsyncClient; // Asynchronous SQS client



    /*
     * Configure inbound flow (requests coming from the manager)
     */
    @Bean
    public QueueChannel inComingRequests() {
        return new QueueChannel();
    }

    @Bean
    public SqsMessageDrivenChannelAdapter sqsInboundMessageHandlerAdaptor() {
        SqsMessageDrivenChannelAdapter adaptor = new SqsMessageDrivenChannelAdapter(sqsAsyncClient,REQUEST_QUEUE);
        return adaptor;
    }

    @Bean
    public JsonToObjectTransformer objectToJsonTransformer() {
        return new JsonToObjectTransformer();

    }


    @Bean
    public IntegrationFlow inboundFlow(ObjectMapper objectMapper) {
        return IntegrationFlow.from(sqsInboundMessageHandlerAdaptor())
                .transform(objectToJsonTransformer())
//                .transform((Message message) -> {
//                    try {
//                        return objectMapper.readValue(message.getFormattedMessage(), StepExecutionRequest.class);
//                    } catch (Exception e) {
//                        throw new RuntimeException("Failed to parse StepExecutionRequest", e);
//                    }
//                })
                .channel(inComingRequests())
                .get();
    }

//    @Bean(name = "workerStep")
//    public Step simpleStep() {
//        return workerStepBuilderFactory.get("workerStep")
//                .inputChannel(inComingRequests())
//                .<, Customer>chunk(100)
//                .reader(itemReader(null))
//                .processor(itemProcessor())
//                .writer(itemWriter())
//                .build();
//    }

}
