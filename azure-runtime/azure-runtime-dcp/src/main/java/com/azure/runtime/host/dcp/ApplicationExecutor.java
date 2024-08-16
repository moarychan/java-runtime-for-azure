package com.azure.runtime.host.dcp;

import com.azure.runtime.host.DistributedApplication;
import com.azure.runtime.host.dcp.k8s.IKubernetesService;
import com.azure.runtime.host.dcp.k8s.KubernetesService;
import com.azure.runtime.host.dcp.logging.AsyncChannel;
import com.azure.runtime.host.dcp.logging.AsyncIterable;
import com.azure.runtime.host.dcp.logging.AsyncIterator;
import com.azure.runtime.host.dcp.logging.CustomFormatter;
import com.azure.runtime.host.dcp.logging.LogEntry;
import com.azure.runtime.host.dcp.logging.ResourceLogSource;
import com.azure.runtime.host.dcp.metadata.DcpDependencyCheckService;
import com.azure.runtime.host.dcp.metadata.DcpInfo;
import com.azure.runtime.host.dcp.metadata.DcpOptions;
import com.azure.runtime.host.dcp.model.common.CustomResource;
import com.azure.runtime.host.dcp.model.common.EnvVar;
import com.azure.runtime.host.dcp.model.container.BuildContext;
import com.azure.runtime.host.dcp.model.container.Container;
import com.azure.runtime.host.dcp.model.container.ContainerPortSpec;
import com.azure.runtime.host.dcp.model.container.PortProtocol;
import com.azure.runtime.host.dcp.model.endpoint.Endpoint;
import com.azure.runtime.host.dcp.model.executable.Executable;
import com.azure.runtime.host.dcp.model.service.AddressAllocationModes;
import com.azure.runtime.host.dcp.model.service.Service;
import com.azure.runtime.host.dcp.process.Pair;
import com.azure.runtime.host.dcp.resource.AppResource;
import com.azure.runtime.host.dcp.resource.ServiceAppResource;
import com.azure.runtime.host.dcp.utils.RandomNameGenerator;
import com.azure.runtime.host.extensions.spring.resources.SpringProject;
import com.azure.runtime.host.resources.DockerFile;
import com.azure.runtime.host.resources.Resource;
import com.azure.runtime.host.resources.annotations.EndpointAnnotation;
import com.azure.runtime.host.resources.annotations.EnvironmentAnnotation;
import com.azure.runtime.host.resources.annotations.KeyValueAnnotation;
import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.util.Watch;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.azure.runtime.host.dcp.utils.ResourceNameGenerator.getObjectNameForResource;

public class ApplicationExecutor {

    private static final Logger LOGGER = Logger.getLogger(ApplicationExecutor.class.getName());
    private static final String DEBUG_SESSION_PORT_VAR = "DEBUG_SESSION_PORT";
    private static final int RANDOM_NAME_SUFFIX_LENGTH = 8;

    //    private final Logger logger;
    private final DistributedApplication model;
    private final Map<String, Resource<?>> applicationModel;
    //    private final IDistributedApplicationLifecycleHook[] lifecycleHooks;
//    private final DistributedApplicationOptions distributedApplicationOptions;
    private final DcpOptions options;
    //    private final DistributedApplicationExecutionContext executionContext;
    private final List<AppResource> appResources = new ArrayList<>();
    //    private final CancellationTokenSource shutdownCancellation = new CancellationTokenSource();
    private final ConcurrentMap<String, Container> containersMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Executable> executablesMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Service> servicesMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Endpoint> endpointsMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<String>> resourceAssociatedServicesMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Resource, Boolean> hiddenResources = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, CompletableFuture<Void>> logStreams = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Logger> loggers = new ConcurrentHashMap<>();
    private final AsyncChannel<LogInformationEntry> logInformationChannel;

    private DcpInfo dcpInfo;
    private Future<?> resourceWatchTask;
    private DcpDependencyCheckService dcpDependencyCheckService;
    private IKubernetesService kubernetesService;
    private Map<String, Pair<Boolean, Boolean>> resourceLogState = new ConcurrentHashMap();
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    private final static Set<String> LOCAL_IGNORE_ENV_KEYS = new HashSet<>(Arrays.asList(
        "eureka.client.serviceUrl.defaultZone",
        "CONFIG_SERVER_URL",
        "MANAGEMENT_ZIPKIN_TRACING_ENDPOINT",
        "MANAGEMENT_PROMETHEUS_METRICS_EXPORT_PUSHGATEWAY_BASE_URL"
    ));

    public ApplicationExecutor(
//            Logger logger,
            DistributedApplication model,
            KubernetesService kubernetesService,
//            List<IDistributedApplicationLifecycleHook> lifecycleHooks,
//            Environment environment,
//            DistributedApplicationOptions distributedApplicationOptions,
            DcpOptions options,
//            DistributedApplicationExecutionContext executionContext,
//            ResourceNotificationService notificationService,
//            ResourceLoggerService loggerService,
            DcpDependencyCheckService dcpDependencyCheckService) {
//        this.logger = logger;
        this.model = model;
        this.applicationModel = model.getResources();
//        this.lifecycleHooks = lifecycleHooks.toArray(new IDistributedApplicationLifecycleHook[0]);
//        this.distributedApplicationOptions = distributedApplicationOptions;
        this.options = options;
//        this.executionContext = executionContext;
        this.dcpDependencyCheckService = dcpDependencyCheckService;
        this.kubernetesService = kubernetesService;
        this.logInformationChannel = new AsyncChannel<>(Executors.newFixedThreadPool(20));
    }

    public void runApplicationAsync() {
//        AspireEventSource.getInstance().dcpModelCreationStart();

        try {
            this.dcpInfo = dcpDependencyCheckService.getDcpInfoAsync().get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {
            prepareServices();
            prepareContainers();
            prepareExecutables();

            publishResourcesWithInitialState();

            watchResourceChanges();

            createServices();
            createContainersAndExecutables();


            for (AppResource appResource : appResources) {
                if (appResource instanceof ServiceAppResource) {
                    ServiceAppResource serviceAppResource = (ServiceAppResource) appResource;
                    Service service = serviceAppResource.getService();
                    System.out.println("Service " + service.getMetadata().getName() + " is running on http://" + service.getSpec().getAddress() + ":" + service.getSpec().getPort());
                }
            }
//            for (IDistributedApplicationLifecycleHook lifecycleHook : lifecycleHooks) {
//                lifecycleHook.afterResourcesCreated(model);
//            }
        } catch (Exception e) {
//            shutdownCancellation.cancel();
            throw e;
        } finally {
//            AspireEventSource.getInstance().dcpModelCreationStop();
        }
    }

    public CompletableFuture<Void> stopAsync() {
//        shutdownCancellation.cancel();
        List<Future<?>> tasks = new ArrayList<>();
        if (resourceWatchTask != null) {
            tasks.add(resourceWatchTask);
        }

//        tasks.addAll(logStreams.values().stream().map(entry -> {
//            entry.getKey().cancel();
//            return entry.getValue();
//        }).collect(Collectors.toList()));

        for (Future<?> task : tasks) {
            try {
                task.cancel(true);
            } catch (Exception e) {
                LOGGER.fine("One or more monitoring tasks terminated with an error. " + e.getMessage());
            }
        }

        return new CompletableFuture<>();
    }

    private void prepareServices() {
        List<Resource<?>> services = model.getResources().values().stream()
                .filter(r -> r.getAnnotations().stream()
                        .anyMatch(EndpointAnnotation.class::isInstance))
                .toList();

        // We need to ensure that Services have unique names (otherwise we cannot really distinguish between
        // services produced by different resources).
        Set<String> serviceNames = new HashSet<>();

        for (Resource<?> sp : services) {
            List<EndpointAnnotation> endpoints = sp.getAnnotations().stream().filter(EndpointAnnotation.class::isInstance)
                    .map(EndpointAnnotation.class::cast)
                    .toList();

            for (EndpointAnnotation endpoint : endpoints) {
                if (!"http".equals(endpoint.getName())) {
                    continue;
                }
                String nameSuffix = RandomNameGenerator.getRandomNameSuffix();
                String candidateServiceName = (endpoints.size() == 1)
                        ? getObjectNameForResource(sp, nameSuffix, "")
                        : getObjectNameForResource(sp, endpoint.getName(), nameSuffix);

                String uniqueServiceName = generateUniqueServiceName(serviceNames, candidateServiceName);
                Service svc = Service.create(uniqueServiceName);
//                Service service = this.kubernetesService.get(Service.class, svc.getMetadata().getName(), null);
//                try {
//                    Thread.sleep(Duration.ofSeconds(10));
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }

                // _options.getValue().isRandomizePorts() 
                // FIXME whether to use randomize ports or not
                Integer port = (false && endpoint.isProxied()) ? null : endpoint.getTargetPort();
                svc.getSpec().setPort(port);
                svc.getSpec().setProtocol(PortProtocol.TCP); // FIXME this is hard code
                svc.getSpec().setAddressAllocationMode(endpoint.isProxied() ? AddressAllocationModes.LOCALHOST.getMode() : AddressAllocationModes.PROXYLESS.getMode());
                svc.getSpec().setAddress(endpoint.isProxied() ? "localhost" : null);

                // So we can associate the service with the resource that produced it and the endpoint it represents.
                svc.annotate(CustomResource.RESOURCE_NAME_ANNOTATION, sp.getName());
                svc.annotate(CustomResource.ENDPOINT_NAME_ANNOTATION, endpoint.getName());

                appResources.add(new ServiceAppResource(sp, svc, endpoint));
            }
        }


    }

    private static String generateUniqueServiceName(Set<String> serviceNames, String candidateName) {
        int suffix = 1;
        String uniqueName = candidateName;

        while (!serviceNames.add(uniqueName)) {
            uniqueName = candidateName + "-" + suffix;
            suffix++;
            if (suffix == 100) {
                // Should never happen, but we do not want to ever get into an infinite loop situation either.
                throw new IllegalArgumentException("Could not generate a unique name for service '" + candidateName + "'");
            }
        }

        return uniqueName;
    }


    private void prepareContainers() {
        List<Resource<?>> modelContainerResources = this.model.getResources()
                .values()
                .stream()
                // only if it's a container or it's a dockerfile
                .filter(r -> r.getClass().equals(com.azure.runtime.host.resources.Container.class)
                        || r instanceof DockerFile)
                .collect(Collectors.toList());

        for (Resource container : modelContainerResources) {

            AppResource containerAppResource = null;

            if (container instanceof DockerFile) {

                if (findBaseImageBuildArg((DockerFile) container)) {
                    continue;
                }

                DockerFile dockerFile = (DockerFile) container;
                String nameSuffix = RandomNameGenerator.getRandomNameSuffix();
                String containerObjectName = getObjectNameForResource(container, nameSuffix, "");
                Container ctr = Container.create(containerObjectName, null);
                BuildContext build = new BuildContext();

//                build.setArgs(buildArgs);
                build.setContext(dockerFile.getContext());
                build.setDockerfile(dockerFile.getPath());
                ctr.getSpec().setBuild(build);
                ctr.getSpec().setContainerName(containerObjectName); // Use the same name for container orchestrator (Docker, Podman) resource and DCP object name.
                ctr.getSpec().setPorts(new ArrayList<>());

                Set<Integer> distinctTargetPorts = new HashSet<>();
                dockerFile.getAnnotations().stream()
                        .filter(a -> a instanceof EndpointAnnotation)
                        .forEach(a -> distinctTargetPorts.add(((EndpointAnnotation) a).getTargetPort()));

                distinctTargetPorts.forEach(
                        port -> {
                            ContainerPortSpec containerPortSpec = new ContainerPortSpec();
                            containerPortSpec.setHostPort(port); // FIXME
                            containerPortSpec.setContainerPort(port);
                            ctr.getSpec().getPorts().add(containerPortSpec);
                        });


                ctr.annotate(CustomResource.RESOURCE_NAME_ANNOTATION, dockerFile.getName());
                ctr.annotate(CustomResource.OTEL_SERVICE_NAME_ANNOTATION, dockerFile.getName());
                ctr.annotate(CustomResource.OTEL_SERVICE_INSTANCE_ID_ANNOTATION, nameSuffix);

                containerAppResource = new AppResource(container, ctr);
            } else if (container instanceof com.azure.runtime.host.resources.Container) {
                String containerImageName = ((com.azure.runtime.host.resources.Container) container).getImage();
//            if (!container()) {
                // This should never happen! In order to get into this loop we need
                // to have the annotation, if we don't have the annotation by the time
                // we get here someone is doing something wrong.
//                throw new IllegalStateException();
//            } else {

//            }

                String nameSuffix = RandomNameGenerator.getRandomNameSuffix();
                String containerObjectName = getObjectNameForResource(container, nameSuffix, "");
                Container ctr = Container.create(containerObjectName, containerImageName);

                ctr.getSpec().setContainerName(containerObjectName); // Use the same name for container orchestrator (Docker, Podman) resource and DCP object name.
                ctr.annotate(CustomResource.RESOURCE_NAME_ANNOTATION, container.getName());
                ctr.annotate(CustomResource.OTEL_SERVICE_NAME_ANNOTATION, container.getName());
                ctr.annotate(CustomResource.OTEL_SERVICE_INSTANCE_ID_ANNOTATION, nameSuffix);

                containerAppResource = new AppResource(container, ctr);
            }

            if (containerAppResource != null) {
                appResources.add(containerAppResource);
            }


            // FIXME: This is a temporary workaround to add the container to the map.
//            setInitialResourceState(container, ctr);
//
//            List<Mount> containerMounts;
//            if (container.tryGetContainerMounts()) {
//                containerMounts = container.getContainerMounts();
//                ctr.getSpec().setVolumeMounts(new ArrayList<>());
//
//                for (Mount mount : containerMounts) {
//                    VolumeMount volumeSpec = new VolumeMount();
//                    volumeSpec.setSource(mount.getSource());
//                    volumeSpec.setTarget(mount.getTarget());
//                    volumeSpec.setType(mount.getType() == ContainerMountType.BIND_MOUNT ? VolumeMountType.BIND : VolumeMountType.VOLUME);
//                    volumeSpec.setReadOnly(mount.isReadOnly());
//
//                    ctr.getSpec().getVolumeMounts().add(volumeSpec);
//                }
//            }


            // FIXME: This is a temporary workaround to add the container to the map.
//            addServicesProducedInfo(container, ctr, containerAppResource);

        }
    }

    private static boolean findBaseImageBuildArg(DockerFile dockerFile) {
        List<EnvVar> buildArgs = (List<EnvVar>) dockerFile.getAnnotations()
                .stream()
                .filter(a -> (a instanceof KeyValueAnnotation && ((KeyValueAnnotation) a).getType().equals("buildArgs")))
                .map(a -> createEnvVar((KeyValueAnnotation) a))
                .collect(Collectors.toList());

        return buildArgs.stream().anyMatch(arg -> arg.getName().equals("BASE_IMAGE"));
    }

    private static EnvVar createEnvVar(KeyValueAnnotation annotation) {
        return new EnvVar(annotation.getKey(), annotation.getValue().toString());
    }

    private void prepareExecutables() {
        List<Resource<?>> springProjects = this.model.getResources()
                .values()
                .stream()
                .filter(r -> r instanceof SpringProject).collect(Collectors.toList());
        for (Resource springProject : springProjects) {
            SpringProject project = (SpringProject) springProject;

            Executable executable = Executable.create(project.getName(), "mvn");
            executable.getSpec().setWorkingDirectory(project.getPath());
            executable.getSpec().setExecutablePath("mvn");
            executable.getSpec().setArgs(List.of("spring-boot:run"));


            Set<EnvVar> envs = new HashSet<>();
            SpringProject spring = (SpringProject) springProject;
            spring.getAnnotations().forEach(annotation -> {
                if (annotation instanceof EnvironmentAnnotation env) {
                    if (!LOCAL_IGNORE_ENV_KEYS.contains(env.getName())) {
                        envs.add(new EnvVar(env.getName(), env.getValue()));
                    }
                }
//                if (annotation instanceof EndpointAnnotation endpoint) {
//                    envs.add(new EnvVar(spring.getName() + "_ENDPOINT", endpoint.getTransport() + "://localhost:" + endpoint.getTargetPort()));
//                }
//                if (annotation instanceof EnvironmentCallbackAnnotation callback) {
//                    Map<String, Object> environmentVariables = new HashMap<>();
//                    callback.getCallback().accept(new EnvironmentCallbackContext(environmentVariables));
//                    environmentVariables.forEach((envKey, envValue) -> envs.add(new EnvVar(envKey, envValue.toString())));
//                }
//                if (annotation instanceof EndpointReferenceAnnotation endpointReference) {
//                    Resource endpointReferenced = endpointReference.getResource();
//                    if (endpointReferenced instanceof EurekaServiceDiscovery eureka) {
//                        EndpointAnnotation endpointAnnotation = eureka.getAnnotations()
//                                .stream()
//                                .map(EndpointAnnotation.class::cast)
//                                .filter(ra -> ra.getName().equals("http"))
//                                .findFirst().get();
//                        String eurekaServer = endpointAnnotation.getTransport() + "://localhost:" + endpointAnnotation.getTargetPort();
//                        envs.add(new EnvVar("EUREKA_CLIENT_SERVICE_URL_DEFAULT_ZONE", eurekaServer));
//                    }
//                    if (endpointReferenced instanceof SpringProject managedComponent && spring.getName().equals("spring-petclinic-config-server")) {
//                        EndpointAnnotation endpointAnnotation = managedComponent.getAnnotations()
//                                .stream()
//                                .map(EndpointAnnotation.class::cast)
//                                .filter(ra -> ra.getName().equals("http"))
//                                .findFirst().get();
//                        String managedComponentUri = endpointAnnotation.getTransport() + "://localhost:" + endpointAnnotation.getTargetPort();
//                        envs.add(new EnvVar("SPRING_CLOUD_CONFIG_URI", managedComponentUri));
//                    }
//                }
//            });
                executable.getSpec().setEnv(envs.stream().toList());

            });

            this.appResources.add(new AppResource(project, executable));
        }
    }

    private void publishResourcesWithInitialState() {
        // Implementation here
    }

    private <T> boolean processResourceChange(String watchEventType, T resource) {

//        System.out.println("Received resource change event: " + watchEventType + " for resource: " + resource);

        if ("ADDED".equals(watchEventType) || "MODIFIED".equals(watchEventType)) {

            if (resource instanceof Container) {
//                System.out.println("Received container resource: " + ((Container) resource).getMetadata().getName());
                Container container = (Container) resource;
                this.containersMap.put(container.getMetadata().getName(), container);
                LogInformationEntry logInformationEntry = new LogInformationEntry(container.getMetadata().getName(), container.isLogsAvailable(), false);
                try {
                    this.logInformationChannel.produce(logInformationEntry).get();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            } else if (resource instanceof Executable) {
//                System.out.println("Received executable resource: " + ((Executable) resource).getMetadata().getName());
                Executable executable = (Executable) resource;
                this.executablesMap.put(executable.getMetadata().getName(), executable);
                LogInformationEntry logInformationEntry = new LogInformationEntry(executable.getMetadata().getName(), executable.isLogsAvailable(), false);
                try {
                    this.logInformationChannel.produce(logInformationEntry).get();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }

        } else if ("DELETED".equals(watchEventType)) {

        }
        return true;
    }

    private void watchResourceChanges() {

        Semaphore outputSemaphore = new Semaphore(1);

        var watchResourcesTask = CompletableFuture.runAsync(() -> {
//            try (outputSemaphore) {
            CompletableFuture.allOf(
                    CompletableFuture.runAsync(() -> watchKubernetesResourceAsync(outputSemaphore, Executable.class, new TypeToken<Watch.Response<Executable>>() {
                    }.getType(), this::processResourceChange)),
                    CompletableFuture.runAsync(() -> watchKubernetesResourceAsync(outputSemaphore, Container.class, new TypeToken<Watch.Response<Container>>() {
                    }.getType(), this::processResourceChange))
//                        CompletableFuture.runAsync(() -> watchKubernetesResourceAsync(Service.class.getSimpleName(), this::processServiceChange)),
//                        CompletableFuture.runAsync(() -> watchKubernetesResourceAsync(Endpoint.class.getSimpleName(), this::processEndpointChange))
            ).join();
//            } catch (Exception e) {
//                logger.error("Error watching resources", e);
//            }
        });

        // FIXME add the logger service
        var watchSubscribersTask = CompletableFuture.runAsync(() -> {
//            try {
//                for (var subscribers : loggerService.watchAnySubscribersAsync(cancellationToken)) {
//                    logInformationChannel.write(new LogInformation(subscribers.getName(), null, subscribers.hasAnySubscribers()));
//                }
//            } catch (Exception e) {
//                LOGGER.severe("Error watching subscribers" + e.getMessage());
//            }
        });

        var watchInformationChannelTask = updateLoggerState();

        resourceWatchTask = CompletableFuture.allOf(watchResourcesTask, watchSubscribersTask, watchInformationChannelTask);
    }

    private CompletableFuture<Void> updateLoggerState() {
        var watchInformationChannelTask = CompletableFuture.runAsync(() -> {

            AsyncIterator<LogInformationEntry> iterator = this.logInformationChannel.iterator();
            while (iterator.hasNext()) {

                iterator.next().thenAccept(entry -> {

                    var logsAvailable = resourceLogState.getOrDefault(entry.getResourceName(), new Pair<>(false, false)).first();
                    var hasSubscribers = resourceLogState.getOrDefault(entry.getResourceName(), new Pair<>(false, false)).second();

                    if (entry.getLogsAvailable() != null) {
                        logsAvailable = entry.getLogsAvailable();
                    }
                    if (entry.getHasSubscribers() != null) {
                        hasSubscribers = entry.getHasSubscribers();
                    }

                    resourceLogState.put(entry.getResourceName(), new Pair<>(logsAvailable, hasSubscribers));


//                    System.out.println("Updating log state for resource: " + entry.getResourceName() + " logsAvailable: " + logsAvailable + " hasSubscribers: " + hasSubscribers);
                    if (Boolean.TRUE.equals(logsAvailable)) {
                        // FIXME this should be replaced by the dashboard
                        try {
                            Path logPath = Path.of("logs/" + entry.getResourceName() + ".log");
                            Files.createDirectories(logPath.getParent());
                            Files.createFile(logPath);
                            FileHandler fileHandler = new FileHandler(logPath.toString(), true);
                            fileHandler.setFormatter(new CustomFormatter());
                            Logger logger = Logger.getLogger(entry.getResourceName());
                            logger.addHandler(fileHandler);

                            loggers.put(logger.getName(), logger);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }


                        if (containersMap.containsKey(entry.getResourceName())) {
//                            System.out.println("Calling startLogStream for container: " + entry.getResourceName());
                            startLogStream(containersMap.get(entry.getResourceName()));
                        } else if (executablesMap.containsKey(entry.getResourceName())) {
//                            System.out.println("Calling startLogStream for executable: " + entry.getResourceName());
                            startLogStream(executablesMap.get(entry.getResourceName()));
                        } else {
                            LOGGER.severe("Resource not found for log streaming: " + entry.getResourceName());
                        }

                        if (Boolean.TRUE.equals(hasSubscribers)) {

                        } else {
//                                    if (logStreams.containsKey(entry.getResourceName())) {
//                                        logStreams.get(entry.getResourceName()).cancel(true);
//                                        logStreams.remove(entry.getResourceName());
//                                    }
//                                    if (containersMap.containsKey(entry.getResourceName()) || executablesMap.containsKey(entry.getResourceName())) {
//                                        loggerService.clearBacklog(entry.getResourceName());
//                                    }
                        }
                    }
                });
            }
        });
        return watchInformationChannelTask;
    }


    private <T extends CustomResource> void watchKubernetesResourceAsync(Semaphore outputSemaphore,
                                                                         Class<T> resourceType,
                                                                         Type watchType,
                                                                         BiConsumer<String, T> handler) {
        try {
            LOGGER.fine("Watching over DCP " + resourceType.getSimpleName() + " resources.");

//            var retryStrategy = RetryStrategy.newBuilder()
//                    .maxAttempts(Integer.MAX_VALUE)
//                    .exponentialBackoff(TimeUnit.MILLISECONDS.toMillis(100), TimeUnit.SECONDS.toMillis(30))
//                    .handleExceptions(EndOfStreamException.class)
//                    .jitter(true)
//                    .onRetry(attempt -> {
//                        logger.debug("Long poll watch operation was ended by server after {} ms (iteration {}).",
//                                attempt.getTotalDelay(), attempt.getAttemptNumber(), attempt.getException());
//                    })
//                    .build();

//            retryStrategy.execute(() -> {

            while (true) {
                Thread.sleep(Duration.ofSeconds(5).toMillis());

                var watch = kubernetesService.watch(resourceType, null, watchType);

                while (watch.hasNext()) {
                    var event = watch.next();
//                    outputSemaphore.acquire();
                    try {
                        handler.accept(event.type, event.object);
                    } finally {
//                        outputSemaphore.release();
                    }
                }

            }
//        );
        } catch (Exception e) {
            LOGGER.severe("Watch task over Kubernetes " + resourceType.getSimpleName() + " resources terminated unexpectedly. Check to ensure dcpd process is running." + e.getMessage());
        } finally {
            LOGGER.fine("Stopped watching " + resourceType.getSimpleName() + " resources.");
        }
    }


    private void createServices() {
        List<ServiceAppResource> needAddressAllocated = appResources.stream().filter(r -> r instanceof ServiceAppResource)
                .filter(r -> {
                    Service svc = ((ServiceAppResource) r).getService();
                    return svc.hasCompleteAddress()
                            && !AddressAllocationModes.PROXYLESS.getMode().equals(svc.getSpec().getAddressAllocationMode());
                })
                .map(r -> (ServiceAppResource) r)
                .toList();

        if (needAddressAllocated.isEmpty()) {
            return;
        }
        needAddressAllocated = new ArrayList<>(needAddressAllocated);
        createResourcesAsync(Service.class);

        List<ServiceAppResource> finalNeedAddressAllocated = needAddressAllocated;
        CountDownLatch latch = new CountDownLatch(finalNeedAddressAllocated.size());

        ExecutorService executorService = Executors.newFixedThreadPool(1);

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                kubernetesService.watch(Service.class, null, new TypeToken<Watch.Response<Service>>() {
                }.getType()).forEachRemaining(event -> {
                    if ("BOOKMARK".equals(event.type)) {
                        return;
                    }

                    Service updated = event.object;
                    Optional<ServiceAppResource> svcOptional = finalNeedAddressAllocated.stream().filter(r -> r.getService().getMetadata().getName().equals(updated.getMetadata().getName())).findFirst();
                    if (!svcOptional.isPresent()) {
                        return;
                    }
                    ServiceAppResource serviceAppResource = svcOptional.get();
                    Service service = serviceAppResource.getService();
                    if (service.hasCompleteAddress()) {
                        service.applyAddressInfoFrom(updated);
                        finalNeedAddressAllocated.remove(serviceAppResource);
                        latch.countDown();
                    }

                    if (finalNeedAddressAllocated.isEmpty()) {

                    }
                });
            }
        });


        try {
            latch.await(Duration.ofSeconds(60).toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            LOGGER.warning("Interrupted while waiting for services to be allocated an address." + e.getMessage());
        }


        for (AppResource appResource : finalNeedAddressAllocated) {
            ServiceAppResource serviceAppResource = (ServiceAppResource) appResource;
            Service service = serviceAppResource.getService();

            Service dcpService = kubernetesService.get(Service.class, service.getMetadata().getName(), null);
            if (dcpService.hasCompleteAddress()) {
                service.applyAddressInfoFrom(dcpService);
            } else {
                LOGGER.warning("Unable to allocate a network port for service " + service.getMetadata().getName() + ". service may be unreachable and its clients may not work properly.");
            }

        }
    }

    private <T extends CustomResource> void createResourcesAsync(Class<T> clazz) {
        Executors.newFixedThreadPool(1).submit(() -> {
            try {
                List<T> resourcesToCreate = appResources.stream()
                        .map(AppResource::getDcpResource)
                        .filter(resource -> resource.getClass().equals(clazz))
                        .map(resource -> (T) resource)
                        .toList();

                if (resourcesToCreate.isEmpty()) {
                    return;
                }

                for (T res : resourcesToCreate) {
                    kubernetesService.create(clazz, res);
                }
            } catch (CancellationException ex) {
                LOGGER.warning("Cancellation during creation of resources." + ex.getMessage());
            }
        });
    }


    class Graph {
        private final List<List<Integer>> adjList;
        private final int n;
        private final List<Integer> inDegree;

        public Graph(int n) {
            this.n = n;
            this.adjList = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                this.adjList.add(new ArrayList<>());
            }
            this.inDegree = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                inDegree.add(0);
            }
        }

        public void addEdge(int u, int v) {
            this.adjList.get(u).add(v);
            this.inDegree.set(v, this.inDegree.get(v) + 1);
        }

        public List<Integer> topologicalSort() {
            List<Integer> inDegree = new ArrayList<>(this.inDegree);

            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (inDegree.get(i) == 0) {
                    order.add(i);
                }
            }

            for (int i = 0; i < order.size(); i++) {
                int u = order.get(i);
                for (int v : adjList.get(u)) {
                    inDegree.set(v, inDegree.get(v) - 1);
                    if (inDegree.get(v) == 0) {
                        order.add(v);
                    }
                }
            }

            return order;
        }

        public int getInDegree(int u) {
            return this.inDegree.get(u);
        }

    }


    private void createContainersAndExecutables() {
        // order the dependencies
        Graph graph = new Graph(appResources.size());
        Map<String, Integer> appResourceIndex = new ConcurrentHashMap<>();
        for (int i = 0; i < appResources.size(); i++) {
            appResourceIndex.put(appResources.get(i).getModelResource().getName(), i);
        }

        for (int i = 0; i < appResources.size(); i++) {
            AppResource appResource = appResources.get(i);
            if (appResource.getModelResource() instanceof SpringProject) {
                SpringProject springProject = (SpringProject) appResource.getModelResource();
                for (Resource dependency : springProject.getDependencies()) {
                    int j = appResourceIndex.get(dependency.getName());
                    graph.addEdge(i, j);
                }
            }
        }
        graph.topologicalSort().reversed().forEach(i -> {
            AppResource appResource = appResources.get(i);
            boolean hasDependency = graph.getInDegree(i) > 0;
            if (appResource.getDcpResource() instanceof Container) {
                Container container = (Container) appResource.getDcpResource();
                this.kubernetesService.create(Container.class, container);
                System.out.println("Container created: " + container.getMetadata().getName());
                try {
                    // FIXME should check the status of the container
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            if (appResource.getDcpResource() instanceof Executable) {
                Executable executable = (Executable) appResource.getDcpResource();
                this.kubernetesService.create(Executable.class, executable);

                if (hasDependency) {
                    while (true) {

                        Executable status = this.kubernetesService.get(Executable.class, executable.getMetadata().getName(), null);
                        try {
                            Thread.sleep(Duration.ofSeconds(3));
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        if ("Running".equals(status.getStatus().getState())) {
                            break;
                        } else {
                            System.out.println("Waiting for executable to be running: " + executable.getMetadata().getName());
                            System.out.println("Current status: " + status.getStatus().getState());
                        }
                    }

                }

                System.out.println("Executable created: " + executable.getMetadata().getName());
            }
        });

//        // Implementation here
//        this.appResources.forEach(appResource -> {
//            
//            
//            if (appResource.getDcpResource() instanceof Container) {
//                Container container = (Container) appResource.getDcpResource();
//                this.kubernetesService.create(Container.class, container);
//                System.out.println("Container created: " + container.getMetadata().getName());
//            }
//
//            if (appResource.getDcpResource() instanceof Executable) {
//                Executable executable = (Executable) appResource.getDcpResource();
//                this.kubernetesService.create(Executable.class, executable);
//                System.out.println("Executable created: " + executable.getMetadata().getName());
//            }
//        });

    }

    private <T extends CustomResource> void startLogStream(T resource) {
        AsyncIterable<List<LogEntry>> enumerable;
        if (resource instanceof Container && ((Container) resource).isLogsAvailable()) {
            enumerable = new ResourceLogSource<>(kubernetesService, dcpInfo.getVersion(), resource);
        } else if (resource instanceof Executable && ((Executable) resource).isLogsAvailable()) {
            enumerable = new ResourceLogSource<>(kubernetesService, dcpInfo.getVersion(), resource);
        } else {
            enumerable = null;
        }

        if (enumerable == null) {
            return;
        }

        logStreams.computeIfAbsent(resource.getMetadata().getName(), key -> {

            var task = CompletableFuture.runAsync(() -> {
                try {
//                    System.out.println("Starting log streaming for " + resource.getMetadata().getName() + " on " + Thread.currentThread().getName());

                    AsyncIterator<List<LogEntry>> iterator = enumerable.iterator();
                    while (iterator.hasNext()) {
                        iterator.next().thenAccept(batch ->
                                {
                                    for (LogEntry entry : batch) {
                                        var level = entry.isErrorMessage() ? Level.SEVERE : Level.INFO;
//                                        loggers.get(resource.getMetadata().getName()).log(level, entry.getContent());
                                    }
                                }
                        );

                    }
//                } catch (OperationCanceledException e) {
//                    logger.debug("Log streaming for {} was cancelled", resource.getMetadata().getName());
                } catch (Exception ex) {
                    LOGGER.severe("Error streaming logs for " + resource.getMetadata().getName());
                    LOGGER.severe(ex.getMessage());
                }
            }, executor);

            return task;
        });
    }

    public CompletableFuture<Void> deleteResourcesAsync() {
        return new CompletableFuture<>();
    }

    private static class LogInformationEntry {
        private final String resourceName;
        private final Boolean logsAvailable;
        private final Boolean hasSubscribers;

        public LogInformationEntry(String resourceName, Boolean logsAvailable, Boolean hasSubscribers) {
            this.resourceName = resourceName;
            this.logsAvailable = logsAvailable;
            this.hasSubscribers = hasSubscribers;
        }

        public String getResourceName() {
            return resourceName;
        }

        public Boolean getLogsAvailable() {
            return logsAvailable;
        }

        public Boolean getHasSubscribers() {
            return hasSubscribers;
        }
    }
}
