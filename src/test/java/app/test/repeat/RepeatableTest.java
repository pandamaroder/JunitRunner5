package app.test.repeat;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Inspired by Artem Sokovets work (github.com/artsok)
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(RepeatExtension.class)
public @interface RepeatableTest {

    /**
     * Placeholder for the current repetition count of a {@code @RepeatedTest}
     * method: <code>{currentRepetition}</code>
     */
    String CURRENT_REPETITION_PLACEHOLDER = "{currentRepetition}";

    /**
     * Placeholder for the total number of repetitions of a {@code @RepeatedTest}
     * method: <code>{totalRepetitions}</code>
     */
    String TOTAL_REPETITIONS_PLACEHOLDER = "{totalRepetitions}";

    /**
     * <em>Short</em> display name pattern for a repeated test: {@value #SHORT_DISPLAY_NAME}
     *
     * @see #CURRENT_REPETITION_PLACEHOLDER
     * @see #TOTAL_REPETITIONS_PLACEHOLDER
     */
    String SHORT_DISPLAY_NAME = "Repetition " + CURRENT_REPETITION_PLACEHOLDER + " of " + TOTAL_REPETITIONS_PLACEHOLDER;

    /**
     * Pool of exceptions
     *
     * @return Exception that handled
     */
    Class<? extends Throwable>[] exceptions() default Throwable.class;

    /**
     * Number of repeats
     *
     * @return N-times repeat test if it failed
     */
    int repeats() default 1;

    /**
     * Minimum success
     *
     * @return After n-times of passed tests will disable all remaining repeats.
     */
    int minSuccess() default 1;


    /**
     * Add break (cool down) to each tests.
     * It matters, when you get some infrastructure problems and you want to run your tests through timeout.
     *
     * @return the length of time to sleep in milliseconds
     */
    long suspend() default 0L;

    /**
     * Display name for test method
     *
     * @return Short name
     */
    String name() default SHORT_DISPLAY_NAME;

}
