// package de.tum.cit.aet.artemis.core.config;
//
//
//
// @Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
// @Configuration
// public class DeferredEagerBeanInitializationConfig {
//
// private static final Logger log = LoggerFactory.getLogger(DeferredEagerBeanInitializationConfig.class);
//
// private final TaskExecutionProperties taskExecutionProperties;
//
// public DeferredEagerBeanInitializationConfig(TaskExecutionProperties taskExecutionProperties) {
// this.taskExecutionProperties = taskExecutionProperties;
// }
//
// @Bean
// public ThreadPoolTaskExecutor deferredEagerInitializationTaskExecutor() {
// log.debug("Creating deferred eager initialization Task Executor");
// ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
// executor.setCorePoolSize(taskExecutionProperties.getPool().getCoreSize());
// executor.setMaxPoolSize(taskExecutionProperties.getPool().getMaxSize());
// executor.setQueueCapacity(taskExecutionProperties.getPool().getQueueCapacity());
// executor.setThreadNamePrefix(taskExecutionProperties.getThreadNamePrefix());
// return executor;
// }
// }
