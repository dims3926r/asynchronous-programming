import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class PR4 {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        System.out.println("=== ЗАВДАННЯ 1 ===");
        runTask1();

        Thread.sleep(2000);

        System.out.println("\n=== ЗАВДАННЯ 2 ===");
        runTask2();
    }

    private static void runTask1() throws ExecutionException, InterruptedException {
        CompletableFuture<List<Integer>> futureOriginal = CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            System.out.println("Start: Генерація масиву...");

            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                list.add(ThreadLocalRandom.current().nextInt(1, 11));
            }

            sleep(500);

            printTime("Генерація масиву", start);
            return list;
        });

        futureOriginal.thenAcceptAsync(list ->
                System.out.println("-> Початковий масив: " + list)
        );

        CompletableFuture<List<Integer>> futureModified = futureOriginal.thenApplyAsync(originalList -> {
            long start = System.nanoTime();
            System.out.println("Start: Модифікація масиву (+5)...");

            List<Integer> modified = originalList.stream()
                    .map(x -> x + 5)
                    .collect(Collectors.toList());

            sleep(300);

            printTime("Модифікація масиву", start);
            return modified;
        });

        futureModified.thenAcceptAsync(list ->
                System.out.println("-> Модифікований масив: " + list)
        );

        CompletableFuture<Void> finalTask = futureOriginal.thenCombineAsync(futureModified, (list1, list2) -> {
            long start = System.nanoTime();
            System.out.println("Start: Обчислення факторіалу суми...");

            int sum1 = list1.stream().mapToInt(Integer::intValue).sum();
            int sum2 = list2.stream().mapToInt(Integer::intValue).sum();
            int totalSum = sum1 + sum2;

            System.out.println("   Сума 1-го масиву: " + sum1);
            System.out.println("   Сума 2-го масиву: " + sum2);
            System.out.println("   Загальна сума для факторіалу: " + totalSum);

            BigInteger factorial = calculateFactorial(totalSum);

            sleep(500);
            printTime("Обчислення факторіалу", start);
            return factorial;
        }).thenAcceptAsync(factorial -> {
            System.out.println("-> Результат ФАКТОРІАЛУ: " + truncateBigNumber(factorial));
        });

        finalTask.get();
    }

    private static void runTask2() throws ExecutionException, InterruptedException {
        long globalStart = System.nanoTime();

        CompletableFuture<List<Integer>> sequenceFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("Start: Генерація послідовності (20 чисел)...");
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                list.add(ThreadLocalRandom.current().nextInt(1, 100));
            }
            sleep(200);
            return list;
        });

        CompletableFuture<Void> logicFuture = sequenceFuture.thenApplyAsync(list -> {
            System.out.println("Інфо: Послідовність згенеровано: " + list);

            int minSum = Integer.MAX_VALUE;
            for (int i = 0; i < list.size() - 1; i++) {
                int pairSum = list.get(i) + list.get(i+1);
                if (pairSum < minSum) {
                    minSum = pairSum;
                }
            }
            sleep(300);
            return minSum;
        }).thenAcceptAsync(minResult -> {
            System.out.println("-> Результат min(a[i] + a[i+1]): " + minResult);
        });

        CompletableFuture<Void> timeReportTask = logicFuture.thenRunAsync(() -> {
            long globalEnd = System.nanoTime();
            double duration = (globalEnd - globalStart) / 1_000_000.0;
            System.out.printf("=== Час роботи усіх асинхронних операцій Завдання 2: %.4f мс ===%n", duration);
        });

        timeReportTask.get();
    }

    private static BigInteger calculateFactorial(int n) {
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    private static void printTime(String taskName, long startTimeNano) {
        long endTime = System.nanoTime();
        double duration = (endTime - startTimeNano) / 1_000_000.0;
        System.out.printf("[Time] %s зайняло: %.4f мс%n", taskName, duration);
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String truncateBigNumber(BigInteger val) {
        String s = val.toString();
        if (s.length() > 50) {
            return s.substring(0, 10) + " ... " + s.substring(s.length() - 10) + " (всього цифр: " + s.length() + ")";
        }
        return s;
    }
}