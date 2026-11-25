package com.xinfei.monolith.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * 单体版专用 Bean 命名生成器。
 *
 * <p>微服务版每个进程只扫描本模块，不同模块存在同名类（如多个 GlobalExceptionHandler）
 * 互不影响；单体把全部模块扫进同一上下文，默认短名会在扫描阶段抛
 * {@link BeanDefinitionStoreException}（ConflictingBeanDefinitionException）。
 *
 * <p>策略：先用 Spring 默认短名；当短名已被另一个不同类占用时，回退为全限定类名。
 * 生成器自身维护已用名映射，不依赖 registry 的即时注册状态，保证同批次扫描也能判重。
 */
public class FqnFallbackBeanNameGenerator extends AnnotationBeanNameGenerator {

    /** 静态共享：判重表跨生成器实例可见。 */
    private static final Map<String, String> USED_BEAN_NAMES = new HashMap<>();

    @Override
    public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
        synchronized (USED_BEAN_NAMES) {
            String defaultName = super.generateBeanName(definition, registry);
            String currentClass = definition.getBeanClassName();
            if (currentClass == null) {
                return defaultName;
            }
            String owner = USED_BEAN_NAMES.get(defaultName);
            if (owner != null && !owner.equals(currentClass)) {
                String fallback = currentClass;
                int suffix = 1;
                while (USED_BEAN_NAMES.containsKey(fallback)) {
                    fallback = currentClass + "#" + suffix++;
                }
                USED_BEAN_NAMES.put(fallback, currentClass);
                return fallback;
            }
            USED_BEAN_NAMES.put(defaultName, currentClass);
            return defaultName;
        }
    }
}
