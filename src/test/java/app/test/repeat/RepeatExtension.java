package app.test.repeat;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.util.Preconditions;
import org.junit.platform.commons.util.StringUtils;
import org.opentest4j.TestAbortedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;
import static org.junit.platform.commons.util.AnnotationUtils.isAnnotated;

/**
 * Inspired by Artem Sokovets work (github.com/artsok)
 */
public class RepeatExtension implements TestTemplateInvocationContextProvider, BeforeTestExecutionCallback,
        AfterTestExecutionCallback, TestExecutionExceptionHandler {

    private int minSuccess = 1;
    private int totalTestRuns = 0;
    private Collection<Class<? extends Throwable>> repeatableExceptions;
    private boolean repeatableExceptionFired = false;
    private DisplayFormatter formatter;
    private final AtomicInteger exceptionFiredCount = new AtomicInteger(0);
    private final AtomicInteger exceptionNotFiredCount = new AtomicInteger(0);
    private long suspend = 0L;

    /**
     * Check that test method contain {@link RepeatableTest} annotation
     *
     * @param extensionContext - encapsulates the context in which the current test or container is being executed
     * @return true/false
     */
    @Override
    public boolean supportsTestTemplate(ExtensionContext extensionContext) {
        return isAnnotated(extensionContext.getTestMethod(), RepeatableTest.class);
    }


    /**
     * Context call TestTemplateInvocationContext
     *
     * @param extensionContext - Test Class Context
     * @return Stream of TestTemplateInvocationContext
     */
    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext extensionContext) {
        Preconditions.notNull(extensionContext.getTestMethod().orElse(null), "Test method must not be null");

        RepeatableTest annotationParams = extensionContext.getTestMethod()
                .flatMap(method -> findAnnotation(method, RepeatableTest.class))
                .orElseThrow(() -> new RuntimeException("The extension should not be executed unless the test method is annotated with @RepeatableTest."));


        int totalRepeats = annotationParams.repeats();
        minSuccess = annotationParams.minSuccess();
        Preconditions.condition(totalRepeats > 0, "Total repeats must be greater than 0");
        Preconditions.condition(minSuccess >= 1, "Total minimum success must be gte 1");

        totalTestRuns = totalRepeats + 1;
        suspend = annotationParams.suspend();

        String displayName = extensionContext.getDisplayName();
        formatter = displayNameFormatter(annotationParams, displayName);

        //Convert logic of repeated handler to spliterator
        Spliterator<TestTemplateInvocationContext> spliterator =
                spliteratorUnknownSize(new TestTemplateIterator(), Spliterator.NONNULL);
        return stream(spliterator, false);
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        final RepeatableTest repeatableTest = context
                .getTestMethod()
                .flatMap(method -> findAnnotation(method, RepeatableTest.class))
                .orElseThrow(() -> new IllegalStateException("The extension should not be executed"));
        repeatableExceptions = new ArrayList<>(Arrays.asList(repeatableTest.exceptions()));

        repeatableExceptions.add(TestAbortedException.class);
    }

    /**
     * Check if exceptions that will appear in test same as we wait
     *
     * @param extensionContext - Test Class Context
     */
    @Override
    public void afterTestExecution(ExtensionContext extensionContext) {
        if (isExceptionFired(extensionContext)) {
            exceptionFiredCount.incrementAndGet();
        } else {
            exceptionNotFiredCount.incrementAndGet();
        }
    }

    private boolean isExceptionFired(ExtensionContext extensionContext) {
        Class<? extends Throwable> exception = extensionContext.getExecutionException()
                .map(Throwable::getClass).orElse(null);
        return exception != null && repeatableExceptions.stream().anyMatch(ex -> ex.isAssignableFrom(exception));
    }

    /**
     * Handler for display name
     *
     * @param test        - RepeatableTest annotation
     * @param displayName - Name that will be represent to report
     * @return RepeatedIfExceptionsDisplayNameFormatter {@link DisplayFormatter}
     */
    private DisplayFormatter displayNameFormatter(RepeatableTest test, String displayName) {
        String pattern = test.name().trim();
        if (StringUtils.isBlank(pattern)) {
            pattern = Optional.of(test.name())
                    .orElseThrow(() -> new RuntimeException("Exception occurred with name parameter of RepeatableTest annotation"));
        }
        return new DisplayFormatter(pattern, displayName);
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        if (appearedExceptionDoesNotAllowRepetitions(throwable)) {
            throw throwable;
        }
        repeatableExceptionFired = true;

        long currentSuccessCount = exceptionNotFiredCount.get();
        if (currentSuccessCount < minSuccess) {
            if (isMinSuccessTargetStillReachable(minSuccess)) {
                throw new TestAbortedException("Do not fail completely, but repeat the test", throwable);
            } else {
                throw throwable;
            }
        }
    }

    /**
     * If exception allowed, will return false
     *
     * @param appearedException - {@link Throwable}
     * @return true/false
     */
    private boolean appearedExceptionDoesNotAllowRepetitions(final Throwable appearedException) {
        return repeatableExceptions.stream().noneMatch(ex -> ex.isAssignableFrom(appearedException.getClass()));
    }

    /**
     * If cannot reach a minimum success target, will return true
     *
     * @param minSuccessCount - minimum success count
     * @return true/false
     */
    private boolean isMinSuccessTargetStillReachable(final long minSuccessCount) {
        return exceptionFiredCount.get() < totalTestRuns - minSuccessCount;
    }

    /**
     * TestTemplateIterator (Repeat test if it failed)
     */
    class TestTemplateIterator implements Iterator<TestTemplateInvocationContext> {
        int currentIndex = 0;

        @Override
        public boolean hasNext() {
            if (currentIndex == 0) {
                return true;
            }
            return exceptionFiredCount.get() > 0 && currentIndex < totalTestRuns;
        }

        @Override
        public TestTemplateInvocationContext next() {
            //If exception appeared would wait suspend time
            if (exceptionFiredCount.get() > 0 && suspend != 0L) {
                try {
                    Thread.sleep(suspend);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            int successfulTestRepetitionsCount = exceptionNotFiredCount.get();
            if (hasNext()) {
                currentIndex++;
                return new InvocationContext(currentIndex, totalTestRuns,
                        successfulTestRepetitionsCount, minSuccess, repeatableExceptionFired, formatter);
            }
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    static class DisplayFormatter {
        private final String pattern;
        private final String displayName;

        public DisplayFormatter(final String pattern, final String displayName) {
            this.pattern = pattern;
            this.displayName = displayName;
        }

        String format(final int currentRepetition, final int totalRepetitions) {
            if (currentRepetition > 1 && totalRepetitions > 0) {
                final String result = pattern
                        .replace(RepeatableTest.CURRENT_REPETITION_PLACEHOLDER, String.valueOf(currentRepetition - 1)) //Minus, because first run doesn't mean repetition
                        .replace(RepeatableTest.TOTAL_REPETITIONS_PLACEHOLDER, String.valueOf(totalRepetitions - 1));
                return this.displayName.concat(" (").concat(result).concat(")");
            } else {
                return this.displayName;
            }
        }
    }

    static class InvocationContext implements TestTemplateInvocationContext {
        private final int currentRepetition;
        private final int totalTestRuns;
        private final int successfulTestRepetitionsCount;
        private final int minSuccess;
        private final boolean repeatableExceptionFired;
        private final DisplayFormatter formatter;

        public InvocationContext(int currentRepetition, int totalRepetitions, int successfulTestRepetitionsCount,
                                 int minSuccess, boolean repeatableExceptionFired,
                                 DisplayFormatter formatter) {
            this.currentRepetition = currentRepetition;
            this.totalTestRuns = totalRepetitions;
            this.successfulTestRepetitionsCount = successfulTestRepetitionsCount;
            this.minSuccess = minSuccess;
            this.repeatableExceptionFired = repeatableExceptionFired;
            this.formatter = formatter;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            return this.formatter.format(this.currentRepetition, this.totalTestRuns);
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return singletonList(new RepeatExecutionCondition(currentRepetition, totalTestRuns, minSuccess,
                    successfulTestRepetitionsCount, repeatableExceptionFired));
        }
    }

    static class RepeatExecutionCondition implements ExecutionCondition {
        private final int totalTestRuns;
        private final int minSuccess;
        private final int successfulTestRepetitionsCount;
        private final int failedTestRepetitionsCount;
        private final boolean repeatableExceptionFired;

        RepeatExecutionCondition(int currentRepetition, int totalRepetitions, int minSuccess,
                                 int successfulTestRepetitionsCount, boolean repeatableExceptionFired) {
            this.totalTestRuns = totalRepetitions;
            this.minSuccess = minSuccess;
            this.successfulTestRepetitionsCount = successfulTestRepetitionsCount;
            this.failedTestRepetitionsCount = currentRepetition - successfulTestRepetitionsCount - 1;
            this.repeatableExceptionFired = repeatableExceptionFired;
        }

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (testUltimatelyFailed()) {
                return ConditionEvaluationResult.disabled("Turn off the remaining repetitions as the test ultimately failed");
            } else if (testUltimatelyPassed()) {
                return ConditionEvaluationResult.disabled("Turn off the remaining repetitions as the test ultimately passed");
            } else {
                return ConditionEvaluationResult.enabled("Repeat the tests");
            }
        }

        private boolean testUltimatelyFailed() {
            return aNonRepeatableExceptionAppeared() || minimalRequiredSuccessfulRunsCannotBeReachedAnymore();
        }

        private boolean aNonRepeatableExceptionAppeared() {
            return failedTestRepetitionsCount > 0 && !repeatableExceptionFired;
        }

        private boolean minimalRequiredSuccessfulRunsCannotBeReachedAnymore() {
            return totalTestRuns - failedTestRepetitionsCount < minSuccess;
        }

        private boolean testUltimatelyPassed() {
            return successfulTestRepetitionsCount >= minSuccess;
        }
    }
}
