// package de.tum.cit.aet.artemis.core.config;
//
//
//
// @Component
// @Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
// public class DeferredEagerBeanInitializer implements ApplicationListener<ApplicationReadyEvent> {
//
// private static final Logger log = LoggerFactory.getLogger(DeferredEagerBeanInitializer.class);
//
// private final ConfigurableApplicationContext context;
//
// private final ThreadPoolTaskExecutor executor;
//
// public DeferredEagerBeanInitializer(ConfigurableApplicationContext context, ThreadPoolTaskExecutor deferredEagerInitializationTaskExecutor) {
// this.context = context;
// this.executor = deferredEagerInitializationTaskExecutor;
// }
//
// @Override
// public void onApplicationEvent(ApplicationReadyEvent event) {
// DefaultListableBeanFactory bf = (DefaultListableBeanFactory) context.getBeanFactory();
//
// for (String name : bf.getBeanDefinitionNames()) {
// BeanDefinition def = bf.getBeanDefinition(name);
// if (def.getRole() == BeanDefinition.ROLE_APPLICATION && def.isSingleton() && def.isLazyInit()) {
//
// executor.submit(() -> {
// try {
// // accessing a bean will trigger its initialization
// context.getBean(name);
// log.debug("Deferred eager initialization of bean {} completed", name);
// }
// catch (Throwable ex) {
// log.warn("Deferred eager initialization of bean {} failed", name, ex);
// }
// });
// }
// }
// executor.setWaitForTasksToCompleteOnShutdown(true);
// executor.shutdown();
// log.info("DeferredBeanPreloader executor shutdown after submitting all tasks");
// }
// }
