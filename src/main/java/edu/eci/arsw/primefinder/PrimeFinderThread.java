package edu.eci.arsw.primefinder;

import java.util.LinkedList;
import java.util.List;

public class PrimeFinderThread extends Thread {

    private final int a, b;
    private final List<Integer> primes;
    private final Object lock;
    private boolean paused;

    public PrimeFinderThread(int a, int b) {
        this(a, b, new Object());
    }

    public PrimeFinderThread(int a, int b, Object lock) {
        super();
        this.primes = new LinkedList<>();
        this.a = a;
        this.b = b;
        this.lock = lock;
        this.paused = false;
    }

    @Override
    public void run() {
        for (int i = a; i < b; i++) {
            if (isPrime(i)) {
                synchronized (primes) {
                    primes.add(i);
                }
                System.out.println(i);
            }

            // Sincronización sobre el monitor compartido (lock) sin busy-waiting
            synchronized (lock) {
                while (paused) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    boolean isPrime(int n) {
        boolean ans;
        if (n > 2) {
            ans = n % 2 != 0;
            for (int i = 3; ans && i * i <= n; i += 2) {
                ans = n % i != 0;
            }
        } else {
            ans = n == 2;
        }
        return ans;
    }

    public void pauseThread() {
        this.paused = true;
    }

    public void resumeThread() {
        this.paused = false;
    }

    public List<Integer> getPrimes() {
        synchronized (primes) {
            return new LinkedList<>(primes);
        }
    }

    public int getPrimesCount() {
        synchronized (primes) {
            return primes.size();
        }
    }
}
