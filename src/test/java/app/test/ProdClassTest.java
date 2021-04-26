package app.test;

import app.test.repeat.RepeatableTest;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProdClassTest {

    ProdClass testedInstance;

    @BeforeEach
    void setUp() {
        testedInstance = new ProdClass();
    }

    @RepeatableTest(repeats = 10)
    public void test() {
        final boolean actual = testedInstance.isNegative(10);

        assertTrue(actual);
    }

    @RepeatableTest
    public void testPass() {
        final boolean actual = testedInstance.isNegative(-10);

        assertTrue(actual);
    }

}