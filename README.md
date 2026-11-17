# DefragX runtime monitoring and recovery demo

DefragX is a JavaFX application for runtime monitoring, event logging, snapshots, and rollback. It demonstrates these capabilities with a simulated transaction workflow; the monitoring and recovery concepts are not limited to banking.

## Run

Requirements: JDK 17 or later and Maven 3.8 or later. From this folder run:

```powershell
mvn javafx:run
```

The app stores its SQLite database at `%LOCALAPPDATA%\DefragX\defragx.db` on Windows. The database contains recovery snapshots and transaction events. A manual snapshot saves the current demo balances and JVM metrics. **Restore Previous Snapshot** prefers the newest manual snapshot; if none exists, it uses the newest valid non-fault snapshot. Automatic snapshots run every three minutes. Heap, process CPU, and live JVM thread readings refresh once per second. CPU is shown as a normalized process-load fraction from 0 to 1 and appears unavailable when the operating system does not provide a reading. System health turns unhealthy if a real monitor threshold is crossed or when the demo issue switch is enabled.

The example transaction workflow uses five demo accounts: Auraa (1111), Supzz (1811), GSV (0701), GGV (0510), and SCN (0515). Transfers affect in-memory demo balances and produce event rows using the computer's local date and time; events persist in SQLite and the UI loads the latest 250 when the app starts. Simulating an unhealthy state saves a recovery checkpoint and randomly changes either the selected sender's or receiver's balance to 0 or -1. The fifth valid transaction also triggers a warning and a simulated balance fault. Restore the saved snapshot to recover. No real bank accounts or payment systems are connected.

## Class diagram implementation

`DefragXAgent`, `RuntimeMonitor`, `RuntimeMetrics`, `SnapshotManager`, `Snapshot`, `StorageManager`, `SQLiteStorage`, `JSONStorage`, `IssueDetector`, and `RollbackManager` implement the supplied diagram's core roles. Restore rolls back the simulated account balances to the selected snapshot; it does not rewind the live JVM. Account balances remain in-memory demo state. Snapshots and transaction events persist in SQLite; the Transactions and Overview event tables load the latest 250 events at startup.
