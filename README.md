# Astra AI

Astra AI is a local JavaFX desktop demonstration of a coordinated team of **100 logical simulated agents** evaluating a finite synthetic candidate-index challenge. The agents are not AI models and do not perform reasoning. The challenge is deterministic and local; it uses no real credentials, external AI APIs, network targets, or third-party services.

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

The application has three primary screens: password entry, initialization/processing, and the final security report. The entered text is used only for a local character-based heuristic; the raw input is cleared and is never stored or transmitted. The independent synthetic challenge does not use that input. The default simulation has eight bounded execution workers, a candidate space of 100,000 indices, and a known synthetic match at index 4,271. Progress and outcome information come from engine snapshots and events. Processing animation reflects the actual engine lifecycle.

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
