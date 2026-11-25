package com.xinfei.monolith.config;

import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

/**
 * 单体 RabbitMQ 监听容器装配。
 *
 * <p>背景：Spring Boot 4 的 {@code RabbitProperties.SimpleContainer} 未暴露
 * {@code consumerStartTimeout}、{@code retryDeclarationInterval} 等启动阻塞参数，
 * 仅能通过容器工厂定制。这里对默认的 {@link SimpleRabbitListenerContainerFactory}
 * 注册 ContainerCustomizer，把每个监听容器的启动等待与声明重试统一调小。</p>
 *
 * <p>效果：broker 不可达时，容器 start 快速失败返回，主线程不被长时间阻塞、
 * 应用照常就绪（含 Netty、CommandLineRunner）；后台 consumer 线程仍按 5s 间隔持续重试，
 * broker 恢复后自动消费。生产环境 broker 可达时 consumer 秒级启动，行为不受影响。</p>
 */
@Configuration(proxyBeanMethods = false)
public class MonolithRabbitConfig implements BeanPostProcessor {

    /** broker 不可达时，start 等待首个 consumer 的超时（毫秒），默认 60000。 */
    private static final long CONSUMER_START_TIMEOUT = 5000L;

    /** consumer 内强制声明（队列/交换器）失败后的重试间隔（毫秒），默认 30000。 */
    private static final long RETRY_DECLARATION_INTERVAL = 5000L;

    /** 声明失败重试间隔（毫秒），默认 5000，这里显式固定。 */
    private static final long FAILED_DECLARATION_RETRY_INTERVAL = 5000L;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof SimpleRabbitListenerContainerFactory) {
            SimpleRabbitListenerContainerFactory factory =
                    (SimpleRabbitListenerContainerFactory) bean;
            ContainerCustomizer<SimpleMessageListenerContainer> customizer = this::tune;
            factory.setContainerCustomizer(customizer);
        }
        return bean;
    }

    private void tune(SimpleMessageListenerContainer container) {
        container.setConsumerStartTimeout(CONSUMER_START_TIMEOUT);
        container.setRetryDeclarationInterval(RETRY_DECLARATION_INTERVAL);
        container.setFailedDeclarationRetryInterval(FAILED_DECLARATION_RETRY_INTERVAL);
    }
}
