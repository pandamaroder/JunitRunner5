package app.test;

import java.util.concurrent.ThreadLocalRandom;

public class ProdClass {

    public boolean isNegative(int a) {
        return ThreadLocalRandom.current().nextBoolean();
    }
}
