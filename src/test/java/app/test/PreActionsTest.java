package app.test;

import app.test.preactions.PreActionHandler;
import app.test.preactions.PreActions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@PreActions(handlers = PreActionsTest.HandlerZ.class)
public class PreActionsTest {

    ProdClass testedInstance;

    @BeforeEach
    void setUp() {
        testedInstance = new ProdClass();
    }

    @Test
    @PreActions(handlers = {
            HandlerA.class,
            HandlerB.class
    })
    void test() {
        testedInstance.isNegative(10);

        assertTrue(true);
    }

    public static class HandlerA implements PreActionHandler {

        @Override
        public void execute() {
            System.out.println("A");
        }
    }

    public static class HandlerB implements PreActionHandler {

        @Override
        public void execute() {
            System.out.println("B");
        }

        @Override
        public int order() {
            return Integer.MIN_VALUE;
        }
    }

    public static class HandlerZ implements PreActionHandler {

        @Override
        public void execute() {
            System.out.println("ZZZ");
        }

        @Override
        public int order() {
            return Integer.MIN_VALUE;
        }
    }
}
