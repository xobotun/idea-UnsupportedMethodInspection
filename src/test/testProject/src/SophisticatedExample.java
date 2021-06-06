import java.util.Collections;
import java.util.List;

import static java.util.Collections.*;

class SophisticatedExample {
    private int oneNothingnessPlease = new IncompleteImplementation().doSomething();

    public static void main(String[] args) {
        Runnable lambda = () -> new IncompleteImplementation().doSomething();

        lambda.run();

        List list = unmodifiableList(emptyList());
        list.remove(1);
    }
}

