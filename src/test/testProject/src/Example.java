class Example {
    public static void main(String[] args) {
        MalformedAbstraction normal = new NormalImplementation();
        normal.doSomething(); // Works flawlessly

        
        MalformedAbstraction broken = new IncompleteImplementation();
        broken.doSomething(); // Suddenly throws exception, but you'll probably know this only at runtime.
    }
}

interface MalformedAbstraction {
    int doSomething();
}

class NormalImplementation implements MalformedAbstraction {
    @Override
    public int doSomething() {
        // Computes something
        return 1;
    }
}

class IncompleteImplementation implements MalformedAbstraction {
    @Override
    public int doSomething() {
        throw new UnsupportedOperationException("There might be a class has this method working");
    }
}
