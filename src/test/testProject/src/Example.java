class Example {
    public static void main(String[] args) {
        MalformedAbstraction normal = new NormalImplementation();
        normal.doSomething(); // Works flawlessly

        MalformedAbstraction broken = new IncompleteImplementation();
        broken.doSomething(); // Suddenly throws exception, but you'll probably know this only at runtime.
    }
}

interface MalformedAbstraction {
    void doSomething();
}

class NormalImplementation implements MalformedAbstraction {
    @Override
    public void doSomething() {
        // Computes something
    }
}

class IncompleteImplementation implements MalformedAbstraction {
    @Override
    public void doSomething() {
        throw new UnsupportedOperationException("There might be a class has this method working");
    }
}
