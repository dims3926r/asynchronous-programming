import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;

class ChunkMultiplier implements Callable<List<Integer>> {
    private final List<Integer> chunk;
    private final int multiplier;

    public ChunkMultiplier(List<Integer> chunk, int multiplier) {
        this.chunk = chunk;
        this.multiplier = multiplier;
    }

    @Override
    public List<Integer> call() throws Exception {
        List<Integer> result = new ArrayList<>();
        String threadName = Thread.currentThread().getName();

        System.out.println("-> Thread (" + threadName + ") processing chunk: " + chunk);

        for (Integer num : chunk) {
            result.add(num * multiplier);
        }

        Thread.sleep(100);

        return result;
    }
}

public class AsyncArrayTask {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter multiplier (int): ");
        int multiplier = scanner.nextInt();

        List<Integer> originalList = generateRandomList();
        System.out.println("\nGenerated list: " + originalList);
        System.out.println("List size: " + originalList.size());

        long startTime = System.currentTimeMillis();

        CopyOnWriteArrayList<Integer> finalResult = new CopyOnWriteArrayList<>();

        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores);

        List<Future<List<Integer>>> futures = new ArrayList<>();

        int chunkSize = 10;

        for (int i = 0; i < originalList.size(); i += chunkSize) {
            int end = Math.min(originalList.size(), i + chunkSize);
            List<Integer> subList = originalList.subList(i, end);

            Callable<List<Integer>> task = new ChunkMultiplier(subList, multiplier);
            futures.add(executor.submit(task));
        }

        collectResults(futures, finalResult);

        executor.shutdown();

        long endTime = System.currentTimeMillis();
        System.out.println("\n------------------------------------------------");
        System.out.println("Final Result: " + finalResult);
        System.out.println("Total execution time: " + (endTime - startTime) + " ms");
        System.out.println("------------------------------------------------");
    }

    private static void collectResults(List<Future<List<Integer>>> futures, CopyOnWriteArrayList<Integer> resultList) {
        for (Future<List<Integer>> future : futures) {
            try {
                while (!future.isDone()) {
                    Thread.sleep(10);
                }

                if (future.isCancelled()) {
                    System.out.println("Task was cancelled.");
                } else {
                    resultList.addAll(future.get());
                }

            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private static List<Integer> generateRandomList() {
        List<Integer> list = new ArrayList<>();
        int size = ThreadLocalRandom.current().nextInt(40, 61); // 40-60 елементів

        for (int i = 0; i < size; i++) {
            list.add(ThreadLocalRandom.current().nextInt(-100, 101)); // -100..100
        }
        return list;
    }
}