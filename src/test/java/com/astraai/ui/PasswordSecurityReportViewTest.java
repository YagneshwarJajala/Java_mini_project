package com.astraai.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;

class PasswordSecurityReportViewTest {
    @Test
    void reportRemovesUnwantedSectionsAndKeepsAnalysisAndAction() throws Exception {
        runOnFxThread(() -> {
            AtomicBoolean actionCalled = new AtomicBoolean();
            PasswordSecurityReportView view = new PasswordSecurityReportView(() -> actionCalled.set(true));
            StackPane root = new StackPane(view);
            new Scene(root, 1280, 900);
            root.applyCss();
            root.resize(1280, 900);
            root.layout();

            String report = view.displayedReportText().toLowerCase();
            assertTrue(report.contains("hashing algorithm analysis"));
            assertFalse(report.contains("synthetic candidate found"));
            assertFalse(report.contains("synthetic candidate result"));
            assertFalse(report.matches("(?s).*CANDIDATE-\\d{5}.*"));
            assertFalse(report.contains("terminal state"));
            assertFalse(report.contains("simulation centerpiece"));
            assertFalse(report.contains("100-agent synthetic search"));
            assertFalse(report.contains("execution summary"));
            assertFalse(report.contains("measured synthetic throughput"));
            assertFalse(report.contains("modeled scenarios"));
            assertFalse(report.contains("modeled crack-time scenario"));
            assertFalse(report.contains("assumed aggregate guesses/s"));
            assertFalse(report.contains("expected discovery"));
            assertTrue(report.contains("illustrative offline rate"));
            assertTrue(report.contains("actual attacker ordering"));
            assertTrue(report.contains("does not hash, guess, or benchmark the submitted password"));
            assertEquals(5, view.algorithmCards().size());
            assertEquals(List.of("Argon2id", "bcrypt", "scrypt", "PBKDF2", "SHA-3-based KDFs"),
                    view.algorithmCards().stream().map(card -> card.info().name()).toList());
            assertFalse(report.contains("security design"));
            assertFalse(report.contains("configuration / trade-off"));
            assertFalse(report.contains("algorithm comparison"));
            for (AlgorithmInfoCard card : view.algorithmCards()) {
                assertTrue(card.modeledTimeText().contains("MODELED CRACK TIME"));
                assertTrue(card.modeledTimeText().contains(
                        card.info().name().equals("SHA-3-based KDFs")
                                ? "Not estimable" : "Estimate unavailable"));
                assertTrue(card.modeledTimeAccessibleHelp().contains(
                        card.info().name().equals("SHA-3-based KDFs")
                                ? "Not estimable" : "Benchmark required"));
                assertFalse(card.modeledTimeText().matches(".*\\d+(?:\\.\\d+)?\\s*ms.*"),
                        "a local verification latency must not appear under the crack-time label");
                assertEquals(CrackTimeEstimator.estimate(card.info()), card.modeledTimeEstimate());
            }
            assertTrue(view.reportContentFitsViewport());

            view.anotherPasswordButton().fire();
            assertTrue(actionCalled.get(), "Analyze Another Password should retain its existing action");
            return null;
        });
    }

    @Test
    void singleAlgorithmCardListRetainsAllOptionsAndWrapsExpandedGuidance() throws Exception {
        runOnFxThread(() -> {
            PasswordSecurityReportView view = new PasswordSecurityReportView(() -> { });
            StackPane root = new StackPane(view);
            new Scene(root, 1280, 900);
            root.applyCss();
            root.resize(1280, 900);
            root.layout();

            List<AlgorithmInfo> catalog = AlgorithmCatalog.all();
            assertEquals(List.of("Argon2id", "bcrypt", "scrypt", "PBKDF2", "SHA-3-based KDFs"),
                    catalog.stream().map(AlgorithmInfo::name).toList());
            String report = view.displayedReportText();
            assertEquals(catalog.stream().map(AlgorithmInfo::name).toList(),
                    view.algorithmCards().stream().map(card -> card.info().name()).toList());
            assertFalse(report.contains("security design"));
            assertFalse(report.contains("configuration / trade-off"));
            assertFalse(report.contains("algorithm comparison"));
            for (AlgorithmInfo info : catalog) assertTrue(report.contains(info.name()));

            for (int index = 0; index < view.algorithmCards().size(); index++) {
                AlgorithmInfo info = catalog.get(index);
                AlgorithmInfoCard card = view.algorithmCards().get(index);
                assertTrue(card.getHeight() > 0 && card.getHeight() < 240,
                        "collapsed card should fit its visible content: " + info.name());
                assertEquals(card.prefHeight(card.getWidth()), card.getHeight(), 1.0,
                        "collapsed card should use its width-aware preferred height: " + info.name());
                String details = card.expandedDetailsText();
                assertTrue(card.modeledTimeText().contains("MODELED CRACK TIME"));
                assertTrue(card.modeledTimeText().contains(
                        info.name().equals("SHA-3-based KDFs")
                                ? "Not estimable" : "Estimate unavailable"));
                assertFalse(card.modeledTimeText().matches(".*\\d+(?:\\.\\d+)?\\s*ms.*"));
                assertTrue(details.contains(info.recommendation()),
                        "full deployment recommendation must remain available for " + info.name());
            }

            AlgorithmInfoCard expandedCard = view.algorithmCards().get(0);
            double collapsedHeight = expandedCard.getHeight();
            expandedCard.setExpanded(true);
            root.applyCss();
            root.layout();
            assertTrue(expandedCard.getHeight() > collapsedHeight,
                    "expanded guidance should contribute to the card's content height");
            assertTrue(expandedCard.visibleContentFits(), "expanded guidance must not overflow its card");
            assertTrue(expandedCard.headerContentFits(), "wrapped suitability must fit at a wide viewport");
            assertTrue(expandedCard.detailValueColumnWidth() > 200,
                    "expanded information should have usable width");

            root.resize(720, 900);
            root.layout();
            assertTrue(view.reportContentFitsViewport(), "narrow reports must not require horizontal scrolling");
            assertTrue(expandedCard.visibleContentFits(), "expanded guidance must remain visible at a narrow width");
            assertTrue(expandedCard.headerContentFits());
            assertTrue(expandedCard.detailValueColumnWidth() > 200);
            return null;
        });
    }

    @Test
    void sha3CatalogEntryIsExplicitlyNotSpecifiedForPasswordStorage() {
        AlgorithmInfo sha3 = AlgorithmCatalog.all().get(4);
        assertTrue(sha3.category().toLowerCase().contains("unspecified"));
        assertTrue(sha3.howItWorks().contains("does not identify a concrete password KDF"));
        assertTrue(AlgorithmCatalog.all().get(4).guidanceLabel().contains("Not recommended"));
        assertTrue(sha3.limitations().contains("SHA-3 digests"));
        assertTrue(sha3.recommendation().contains("insufficiently specified"));
        assertTrue(sha3.recommendation().contains("plain SHA-3 digest"));
    }

    @Test
    void analysisUpdatesEstimatesWithoutShowingSyntheticCandidateResults() throws Exception {
        PasswordAnalysis first = PasswordInputPolicy.analyzeAndErase("longer-Example-47!".toCharArray()).orElseThrow();
        PasswordAnalysis second = PasswordInputPolicy.analyzeAndErase("aaaaaaa".toCharArray()).orElseThrow();

        runOnFxThread(() -> {
            PasswordSecurityReportView view = new PasswordSecurityReportView(() -> { });
            view.update(first);
            String firstAssessment = view.displayedAssessmentText();
            List<String> firstCrackTimes = view.algorithmCards().stream()
                    .map(card -> card.crackTimeEstimate().displayText()).toList();
            for (int index = 0; index < 4; index++) {
                assertEquals(CrackTimeEstimate.Status.ESTIMATED,
                        view.algorithmCards().get(index).crackTimeEstimate().status());
            }
            assertTrue(firstCrackTimes.get(0).equals("Over 1,000 years"));
            assertEquals("Not estimable", firstCrackTimes.get(4));
            assertFalse(view.displayedReportText().toLowerCase().contains("synthetic candidate found"));
            assertFalse(view.displayedReportText().toLowerCase().contains("synthetic candidate result"));
            assertFalse(view.displayedReportText().matches("(?s).*CANDIDATE-\\d{5}.*"));
            assertTrue(view.displayedReportText().contains("did not store or hash the submitted password"));

            view.update(second);
            for (int index = 0; index < 4; index++) {
                assertNotEquals(firstCrackTimes.get(index),
                        view.algorithmCards().get(index).crackTimeEstimate().displayText());
            }
            assertEquals("Not estimable", view.algorithmCards().get(4).crackTimeEstimate().displayText());
            assertNotEquals(firstAssessment, view.displayedAssessmentText());
            assertTrue(view.displayedAssessmentText().contains(second.explanation()));
            assertFalse(view.displayedAssessmentText().contains(first.explanation()));
            String refreshedReport = view.displayedReportText().toLowerCase();
            assertFalse(refreshedReport.contains("measured synthetic throughput"));
            assertFalse(refreshedReport.contains("search-space coverage"));
            assertTrue(refreshedReport.contains("hashing algorithm analysis"));
            return null;
        });
    }

    private static <T> T runOnFxThread(java.util.concurrent.Callable<T> action) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
            assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX toolkit should start");
        } catch (IllegalStateException alreadyStarted) {
            // Another test or application bootstrap already initialized JavaFX.
        }
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(60, TimeUnit.SECONDS);
    }
}
