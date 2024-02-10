/* Name: Ryan Monahan
Course: CNT 4714 Spring 2024
Assignment title: Project 2 – Synchronized, Cooperating Threads Under Locking
Due Date: February 11, 2024
*/

package app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {
    public static void main(String[] args) {
        BankAccount account = new BankAccount();
        ExecutorService executorService = Executors.newFixedThreadPool(17); // 5 Depositors + 10 Withdrawals + 2 Auditors

        // Create and start threads
        for (int i = 0; i < 5; i++) {
            executorService.submit(new Depositor(account, "Agent DT" + (i+1)));
        }

        for (int i = 0; i < 10; i++) {
            executorService.submit(new Withdrawal(account, "Agent WT" + (i+1)));
        }

        executorService.submit(new Auditor(account));
        executorService.submit(new Auditor(account));

        // Simulate for some time (manually stop the program)
        try {
            Thread.sleep(60000); // Adjust the simulation duration as needed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        executorService.shutdownNow(); // Stop all threads
    }
}

class BankAccount {
    private double balance;
    private int transactionCount = 0;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition sufficientFunds = lock.newCondition(); // Condition for signaling
    private static final String TRANSACTION_LOG_FILE = "transactions.csv";

    public BankAccount() {
        this.balance = 0.0;
        initializeLogFile();
    }

    private void initializeLogFile() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(TRANSACTION_LOG_FILE, false))) { // false to overwrite each time
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deposit(double amount, String name) {
        lock.lock();
        try {
            balance += amount;
            transactionCount++;
            if (amount > 350) {
                logTransaction(transactionCount, "deposit", amount, name);
            }
            sufficientFunds.signalAll();
            System.out.println(name + " deposited $" + amount + ". New balance: $" + getBalance());
        } finally {
            lock.unlock();
        }
    }
    
    public boolean withdraw(double amount, String name) {
        lock.lock();
        try {
            if (balance < amount) {
                // If balance is less than amount, print message and return false
                System.out.println(name + " attempted to withdraw $" + amount + " - insufficient funds. Thread stopping.");
                return false; // Returning false indicates the withdrawal did not proceed
            }
            balance -= amount;
            transactionCount++;
            if (amount > 75) {
                logTransaction(transactionCount, "withdraw", amount, name);
            }
            System.out.println(name + " withdrew $" + amount + ". New balance: $" + getBalance());
            sufficientFunds.signalAll(); // Notify other threads that may be waiting for a balance change
            return true; // Successful withdrawal
        } finally {
            lock.unlock();
        }
    }
    
    
    

    private void logTransaction(int transactionNumber, String type, double amount, String name) {
        // Create a date/time string formatted as shown in the screenshot
        String dateTimeString = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    
        // Format the transaction log entry as per the screenshot
        String logEntry = String.format("%s %s issued %s of $%.2f at %s EST Transaction Number : %d\n",
                                        type.equals("deposit") ? "Depositor Agent" : "Withdrawal Agent",
                                        name,
                                        type,
                                        amount,
                                        dateTimeString,
                                        transactionNumber);
    
        // Write the formatted string to the log file
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(TRANSACTION_LOG_FILE, true))) {
            bw.write(logEntry);
        } catch (IOException e) {
            System.err.println("An error occurred while logging transaction to file: " + e.getMessage());
        }
    }
    

    public double getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }
}


class Depositor implements Runnable {
    private final BankAccount account;
    private final Random random = new Random();
    private final String name; // Name of the depositor

    public Depositor(BankAccount account, String name) {
        this.account = account;
        this.name = name;
    }

    @Override
    public void run() {
        while (true) {
            double amount = 1 + random.nextDouble() * 500; // Deposit amount logic
            account.deposit(amount, name); // Pass name to deposit
            try {
                TimeUnit.MILLISECONDS.sleep(1000 + random.nextInt(1000)); // Sleep between 500 to 1000 ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}



class Withdrawal implements Runnable {
    private final BankAccount account;
    private final Random random = new Random();
    private final String name; // Name of the withdrawal agent

    public Withdrawal(BankAccount account, String name) {
        this.account = account;
        this.name = name;
    }

    @Override
    public void run() {
        while (true) {
            double amount = 1 + random.nextDouble() * 99; // Withdrawal amount logic
            try {
                account.withdraw(amount, name); // Pass name to withdraw
                TimeUnit.MILLISECONDS.sleep(random.nextInt(500)); // Random sleep to simulate processing time
            } catch (InterruptedException e) {
                System.out.println(name + " interrupted.");
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}



class Auditor implements Runnable {
    private final BankAccount account;
    private int transactionsSinceLastAudit = 0;
    private final int auditInterval = 100; // Adjust as needed

    public Auditor(BankAccount account) {
        this.account = account;
    }

    @Override
    public void run() {
        while (true) {
            try {
                TimeUnit.MILLISECONDS.sleep(auditInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            double balance = account.getBalance();
            transactionsSinceLastAudit++;
            System.out.println("Audit - Balance: $" + balance + ", Transactions: " + transactionsSinceLastAudit);
        }
    }
}

