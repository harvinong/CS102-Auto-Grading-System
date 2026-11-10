package autogradesys.io;

/**
 * There are cases where instructor only get the compiled binaries instead of the source code.
 * So this class is made to remind instructor to install the JDK, which includes the compiler.
 */
public class CompilerNotFoundException extends RuntimeException {

    @Override
    public String getMessage() {
        return "Compiler not found. Please install the latest JDK";
    }

}
