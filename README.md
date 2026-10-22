# PassScope

**Password Security & Hash Analysis**

PassScope is a local JavaFX desktop demonstration of a coordinated team of **100 logical simulated agents** evaluating a finite synthetic candidate-index challenge. The agents are not AI models and do not perform reasoning. The challenge is deterministic and local; it uses no real credentials, external AI APIs, network targets, or third-party services.

## Requirements

- JDK 21
- Apache Maven
- A graphical desktop session to launch JavaFX

## Build and run

Run the automated tests:

```powershell
mvn test
```

Launch the JavaFX application:

```powershell
mvn javafx:run
```

The application has three primary screens: password entry, initialization/processing, and the final security report. The entered text is handled transiently for a local character-based heuristic; the UI fields are cleared after submission, and no password is persisted or transmitted. A mutable working copy is cleared after analysis, but Java `String` copies held by the UI/JVM cannot be reliably wiped. The independent synthetic challenge does not use that input. The default simulation has eight bounded execution workers, a candidate space of 100,000 indices, and a known synthetic match at index 4,271. Progress and outcome information come from engine snapshots and events. Processing animation reflects the actual engine lifecycle.

The final report shows a local password-characteristic assessment and one concise, expandable card for each of Argon2id, bcrypt, scrypt, PBKDF2, and an explicitly unspecified SHA-3-based KDF entry. The four supported cards calculate an approximate time under the explicitly illustrative, single-attacker offline assumptions in `CrackTimeAssumptionDataset`; SHA-3 remains “Not estimable” because no concrete password KDF is specified. These are educational model outputs, not measured performance, true entropy, actual attacker ordering, or predictions. The 100 logical agents and measured throughput belong only to the independent synthetic search; they are never used as password-guessing speed. The cards do not hash, guess, or benchmark the submitted password. Argon2id is presented as a strong modern starting point when its parameters and operational requirements fit; the SHA-3 entry is not recommended for direct password storage until a concrete password KDF and parameters are identified.

### Educational crack-time model

`CrackTimeAssumptionDataset` version `educational-reference-v1` (as of 2026-10-21) is an internal, illustrative assumption set authored for this demonstration. It is not externally sourced, measured, or representative of a particular attack rig. The modeled offline rate is for one hypothetical attacker processing one verifier at a time:

| Algorithm and assumed configuration | Assumed guesses per second |
| --- | ---: |
| Argon2id v1.3, m=65,536 KiB, t=3, p=1 | 10 |
| bcrypt 2b, cost=12 | 10 |
| scrypt N=131,072, r=8, p=1 | 5 |
| PBKDF2-HMAC-SHA-256, 600,000 iterations | 50 |

The values are deliberately labeled educational assumptions, not benchmark facts. They can be replaced by constructing a `CrackTimeAssumptionDataset` with different algorithm-specific profiles; the Results page uses the defaults above. The model counts the observed classes in the local heuristic and assumes candidate characters are drawn uniformly from their full printable-ASCII classes (26 lowercase, 26 uppercase, 10 digits, and 33 other printable characters). If the heuristic detects a repeated-character or simple ascending/descending sequence, it instead uses the explicitly simplified family of repeated-character strings or two-direction sequences from the observed classes. Expected work is half the modeled candidate space, rounded up. This convention does not describe the ordering used by a real attacker. Non-printable-ASCII input is unavailable to this model; SHA-3 remains not estimable. Durations are rounded to concise units using 30.4375 days per month and 365.25 days per year; values beyond the modeled 1,000-year display threshold are shown as a lower bound. The local KDF verification benchmark and synthetic search are not inputs to this calculation.

### Password-KDF cost data

`PasswordKdfCostDataset` separates required configuration schemas from actual measurement observations. Argon2id requires an explicit version, memory in bytes, time cost, parallelism, salt/output lengths, and provider/version. bcrypt requires a positive work factor, explicit salt-handling choice, salt length, output format, and provider/version. scrypt requires power-of-two N greater than 1, positive r and p, explicit memory context in bytes, salt/output lengths, and provider/version. PBKDF2 requires an exact PRF/hash variant, positive iteration count, salt/output lengths, and provider/version. SHA-3 remains unsupported/underspecified because no concrete password-KDF construction is defined. Configuration objects validate generic inputs; provider-specific compatibility is checked again by the local benchmark runner.

### Explicit local KDF verification benchmark

An offline developer benchmark is available through `KdfVerificationBenchmark`. It uses Bouncy Castle `bcprov-jdk18on` 1.86 for Argon2id, bcrypt, and scrypt; PBKDF2 uses the explicitly selected installed JCA provider (for example, `SunJCE`) and captures its runtime version. SHA-3 is unavailable. The Bouncy Castle lightweight API runs are pinned to the selected artifact/version; Argon2 v1.3 is the only supported Argon2 version in this runner. The benchmark safety limits are Argon2id memory up to 256 MiB, time cost up to 6, and parallelism up to 8; bcrypt work factor 4–16; scrypt working memory up to 256 MiB and p up to 4; PBKDF2 iteration count up to 2,000,000; salt and output lengths up to 1,024 bytes; warm-up count 1–5; sample count 3–31; elapsed-run setting 1,000–60,000 ms. These are execution limits, not recommended KDF settings or claims about provider-supported ranges. The deadline is checked between KDF operations, so one operation can extend beyond the configured deadline.

The benchmark command is opt-in and is never run by application startup, `mvn test`, or `mvn package`. This explicit PBKDF2 invocation illustrates the required inputs; its values are command-line choices for that run, not application defaults or security recommendations:

```powershell
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.mainClass=com.astraai.ui.KdfVerificationBenchmark" "-Dexec.args=--algorithm=PBKDF2 --prf-hash-variant=HmacSHA256 --iterations=100000 --salt-length-bytes=16 --output-length-bytes=32 --provider-name=SunJCE --warmup-count=1 --sample-count=5 --max-duration-ms=10000 --output=kdf-pbkdf2-observation.json"
```

Each completed run prints a concise console summary and creates a versioned JSON report. The optional `--output=<path>` is relative to the current working directory unless absolute; its parent directory must already exist. If omitted, the runner chooses `kdf-verification-<algorithm>-<UTC timestamp>.json` in the current directory. An existing report is never overwritten: creation fails clearly instead. Failed or partial runs are not emitted as completed measurements; where possible, the runner writes a `FAILED` report with safe failure information and no measurement values.

The JSON schema is version `1.0`. Here is the report shape with explicitly illustrative placeholders—not measured values:

```json
{
  "schemaVersion": "1.0",
  "status": "<COMPLETED or FAILED>",
  "failureReason": null,
  "timestamp": "<actual ISO-8601 timestamp>",
  "observationType": "local_kdf_verification_cost",
  "algorithm": "<actual algorithm>",
  "variant": "<actual variant and version>",
  "parameters": {
    "algorithmVersion": "<actual algorithm version>",
    "memoryBytes": "<actual configured memory in bytes>",
    "timeCost": "<actual configured time cost>",
    "parallelism": "<actual configured parallelism>",
    "saltLengthBytes": "<actual salt length in bytes>",
    "outputLengthBytes": "<actual output length in bytes>"
  },
  "provider": { "name": "<actual provider>", "version": "<actual version>" },
  "javaRuntime": { "name": "<actual runtime>", "version": "<actual version>" },
  "operatingSystem": {
    "name": "<actual OS>",
    "version": "<actual OS version>",
    "architecture": "<actual architecture>"
  },
  "cpuIdentifier": { "availability": "unavailable", "value": "unavailable" },
  "jvmInputArguments": { "availability": "<available or unavailable>", "values": ["<actual argument>"] },
  "warmupCount": "<actual count>",
  "sampleCount": "<actual count, null if failed>",
  "measuredDuration": {
    "value": "<nanoseconds, null if failed>",
    "unit": "nanoseconds",
    "definition": "<fixture generation through statistics aggregation, measured by System.nanoTime()>"
  },
  "verificationLatency": {
    "median": "<nanoseconds per verification, null if failed>",
    "p90": "<nearest-rank nanoseconds per verification, null if failed>",
    "p90Method": "nearest-rank",
    "unit": "nanoseconds per verification"
  },
  "maximumDurationMillis": "<configured deadline in milliseconds>",
  "limitations": ["<interpretation and measurement limitations>"]
}
```

The report records the exact KDF parameters (including byte units), algorithm/variant, configured provider name and actual provider version, timestamp, runtime and platform metadata, JVM input arguments, warm-up/sample counts, total measured duration, and the statistics from the completed sample set. `measuredDuration` covers fixture generation, initial derivation, warm-ups, timed verifications, and aggregation; the per-verification median and p90 come from timed verification samples. `maximumDurationMillis` is the configured deadline, not an observed duration. CPU identity is explicitly `unavailable` because the runner has no reliable portable local source. Unavailable JVM arguments are represented by an availability marker and the value `unavailable`; an available empty list means no JVM input arguments were reported.

The benchmark generates a fresh random fixture password and salt for each run, then records only non-secret configuration and measurement metadata. Password, salt, derived hash, and fixture secrets are not included in JSON or console output. JSON output is escaped and field ordering is stable. A report is marked `COMPLETED` only after all requested samples and aggregation succeed; failed runs have `FAILED` status and null measurement values. Reports are local verification-cost observations, not universal performance claims. Results vary with the machine, JVM, provider/version, parameters, and run conditions. This latency is not password-cracking time, candidate throughput, or attack-rate data. The configured deadline is checked between KDF operations, so a single operation may exceed it.

Other algorithm-specific required options are:

- Argon2id: `--version`, `--memory-bytes`, `--time-cost`, `--parallelism`, `--salt-length-bytes`, and `--output-length-bytes`.
- bcrypt: `--work-factor`, `--salt-handling=GENERATED_PER_HASH_AND_EMBEDDED_IN_OUTPUT`, `--salt-length-bytes=16`, and `--output-format=OpenBSDBCrypt-2y-60-char`.
- scrypt: `--N`, `--r`, `--p`, `--memory-bytes` (must equal `128 * N * r`), `--memory-context`, `--salt-length-bytes`, and `--output-length-bytes`.
- PBKDF2: `--prf-hash-variant` (for example, the explicit `HmacSHA256` choice), `--iterations`, `--salt-length-bytes`, `--output-length-bytes`, and `--provider-name`.

Every command also requires `--warmup-count` (1–5), `--sample-count` (3–31), and `--max-duration-ms` (1,000–60,000). The runner generates a random test password and salt at runtime, creates a test hash, and repeatedly verifies only that same fixture. It reports a local verification latency median and p90, sample count, parameters, timestamp, provider, Java runtime, OS, architecture, and method. It never reports the password, salt, derived hash, candidate throughput, or crack time. Results are machine- and run-specific, sensitive to hardware, JVM, provider, parameters, and background load; CPU identity is explicitly reported unavailable. Failed or partial runs produce no successful observation.

The educational crack-time model is separate from this benchmark. Benchmark observations are not connected to its assumed offline rate, and neither benchmark latency nor synthetic-search throughput or the 100 logical agents are used in the estimate.

### Algorithm-card result states

The existing `MODELED CRACK TIME` field shows an approximate duration for the four algorithms under the educational assumptions above. Before a local analysis is available, or when its candidate-space inputs are unsupported, it shows **Estimate unavailable**. The SHA-3-based entry says **Not estimable** because no concrete password KDF is specified. The field never displays KDF verification latency.

`KdfBenchmarkResultState` separately describes benchmark evidence:

- **Benchmark unavailable** — the chosen algorithm/configuration/provider is unsupported by the local runner, or the configuration does not match the card's algorithm.
- **Benchmark required** — the configuration is supported, but no matching completed report with current-runtime provenance is supplied. A missing selected configuration also remains in this state.
- **Local verification-cost observation** — returned only for a completed report whose algorithm, variant, every parameter, provider name/version, Java runtime, OS, architecture, CPU metadata, and JVM-argument metadata exactly match the selected configuration and current runtime. The value is explicitly named “Median local KDF verification” in milliseconds and marked “not crack time.”
- **Not estimable** — reserved for the still-underspecified SHA-3 entry.
- **Estimate unavailable** — no local analysis, unsupported input, or missing educational rate profile prevents a calculation.

Failed and test-fixture reports are rejected; report status, complete measurement fields, exact parameters, provider version, and provenance must all match. The Results page does not display local benchmark latency. Its crack-time calculation uses only the educational assumption dataset and the derived local character analysis; it does not derive attack speed from benchmark measurements. Local verification latency measures repeat verification of the benchmark's own known fixture under one configuration; it cannot be converted into guesses per second or time-to-crack, and is not evidence about the submitted password.

The exact-match transition can be checked separately from the standard test/package lifecycle with this explicit, bounded local PBKDF2 smoke command (it generates its own random fixture and does not write a report):

```powershell
mvn test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.mainClass=com.astraai.ui.KdfBenchmarkResultStateSmoke" "-Dexec.classpathScope=test"
```

This smoke command runs an actual short local verification-cost benchmark; do not run it as part of routine Maven validation.

### Explicit benchmark-report loading

An in-process caller may explicitly inspect one selected report file with:

```java
KdfBenchmarkResultState state = KdfBenchmarkReportLoader.load(
        selectedReportPath, selectedAlgorithm, selectedValidatedConfiguration);
```

The caller supplies the `Path`, expected catalog algorithm, and already selected configuration. The loader reads only that path; it does not search a directory, select the newest file, choose defaults, persist results, run a KDF, or connect to JavaFX startup/Results rendering. Files larger than 1 MiB, missing/non-regular files, malformed JSON, unsupported schema versions, unsupported parameter shapes, non-completed/test-fixture/failed reports, incomplete metadata, and invalid measurements are rejected with safe typed status/reason text. It never includes the full path or report body in a failure message.

For an accepted observation the loader compares the report against the selected configuration and locally available provider/runtime metadata: algorithm, exact variant, every parameter, provider name/version, Java runtime, OS name/version, architecture, CPU-identifier availability/value, and JVM-argument availability/values. Core Java/OS/architecture metadata must be present and known. CPU identity may remain explicitly unavailable only when that is also the current runtime's state. Any mismatch is rejected; missing expected configuration returns **Benchmark required**, and unsupported configuration or invalid report returns **Benchmark unavailable**. SHA-3 remains **Not estimable**. The loader does not make an unsupported report usable.

Reports use the existing JSON schema and are unsigned. Metadata matching establishes compatibility only; it does not establish authenticity or tamper resistance. The loader validates structure, status, required fields, numeric ranges/units, and exact self-reported provenance, but cannot independently prove that an external report was produced by an unmodified runner. Only load files from a source you trust. Even an accepted result is solely a local KDF verification-latency observation; it is never a crack-time estimate and is not shown in the existing card.

## Project structure

```text
src/main/java/com/astraai/
  Main.java                 JavaFX application launcher
  App.java                  Application and screen lifecycle
  ui/                       JavaFX entry, processing, and report screens
  simulation/               Java-only challenge, coordination, engine, and event model
src/test/java/              JUnit 5 tests
```

JavaFX presentation styling is applied directly by the Java UI components.

The simulation package has no JavaFX dependencies. `SimulationEngine.subscribe` supplies an initial immutable snapshot and asynchronous sequenced updates. The JavaFX layer dispatches those updates with `Platform.runLater`; when updates are coalesced or a subscriber is resynchronized, it retrieves a fresh engine snapshot. The activity feed retains at most 500 entries.

## Safety and scope

The synthetic candidate strings use the form `CANDIDATE-#####`. The submitted text is analyzed locally and transiently to count length and character classes; it is not submitted to the synthetic challenge, persisted, or transmitted. No account, network service, or external target is contacted. No external AI model or paid AI service is used. Participants are simulated and the UI does not display fabricated thoughts or reasoning transcripts.
