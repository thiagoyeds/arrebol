package org.fogbowcloud.arrebol;

import org.apache.log4j.Logger;
import org.fogbowcloud.arrebol.models.job.Job;
import org.fogbowcloud.arrebol.models.job.JobState;
import org.fogbowcloud.arrebol.models.specification.Specification;
import org.fogbowcloud.arrebol.models.task.Task;
import org.fogbowcloud.arrebol.models.task.TaskState;
import org.fogbowcloud.arrebol.repositories.JobRepository;
import org.fogbowcloud.arrebol.resource.MatchAnyResource;
import org.fogbowcloud.arrebol.resource.Resource;
import org.fogbowcloud.arrebol.queue.TaskQueue;
import org.fogbowcloud.arrebol.resource.ResourcePool;
import org.fogbowcloud.arrebol.resource.StaticPool;
import org.fogbowcloud.arrebol.scheduler.DefaultScheduler;
import org.fogbowcloud.arrebol.scheduler.FifoSchedulerPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

public class ArrebolController {

    private final Logger LOGGER = Logger.getLogger(ArrebolController.class);

    private final Properties properties;
    private final DefaultScheduler scheduler;
    private final Map<String, Job> jobPool;
    private final TaskQueue queue;

    private final Timer jobDatabaseCommitter;

    private static final int COMMIT_PERIOD_MILLIS = 1000 * 20;//20 seconds

    @Autowired
    private JobRepository jobRepository;


    public ArrebolController(Properties properties) {
        this.properties = properties;

        String queueId = UUID.randomUUID().toString();
        String queueName = "defaultQueue";

        this.queue = new TaskQueue(queueId, queueName);

        //FIXME: we are missing something related to worker/resource func
        int poolId = 1;
        Collection<Resource> resources = createPool(5, poolId);
        ResourcePool pool = new StaticPool(poolId, resources);

        //create the scheduler bind the pieces together
        FifoSchedulerPolicy policy = new FifoSchedulerPolicy();
        this.scheduler = new DefaultScheduler(queue, pool, policy);

        this.jobPool = new HashMap<String,  Job>();
        this.jobDatabaseCommitter = new Timer(true);
    }

    private Collection<Resource> createPool(int size, int poolId) {
        Collection<Resource> resources = new LinkedList<Resource>();
        int poolSize = 5;
        Specification resourceSpec = null;
        for (int i = 0; i < poolSize; i++) {
            resources.add(new MatchAnyResource(resourceSpec, "resourceId-"+i, poolId));
        }
        return resources;
    }

    public void start() {

        Thread schedulerThread = new Thread(this.scheduler, "scheduler-thread");
        schedulerThread.start();

        //commit the job pool to DB using a COMMIT_PERIOD_MILLIS PERIOD between successive commits
        //(I also specified the delay to the start the fist commit to be COMMIT_PERIOD_MILLIS)
        this.jobDatabaseCommitter.schedule(new TimerTask() {
                    public void run() {
                        LOGGER.info("Commit job pool to the database");
                        for(Job job : jobPool.values()) {
                            jobRepository.save(job);
                        }
                    }
                }, COMMIT_PERIOD_MILLIS, COMMIT_PERIOD_MILLIS
        );

        // TODO: read from bd
    }

    public void stop() {
        // TODO: delete all resources?
    }

    public String addJob(Job job) {

        job.setJobState(JobState.READY);
        this.jobPool.put(job.getId(), job);

        for(Task task : job.getTasks().values()){
            this.queue.addTask(task);
        }

        return job.getId();
    }

    public String stopJob(Job job) {

        Map<String, Task> taskMap = job.getTasks();
        for(Task task : taskMap.values()){
         //
        }
        return job.getId();
    }

    public TaskState getTaskState(String taskId) {
        //FIXME:
        return null;
    }
}