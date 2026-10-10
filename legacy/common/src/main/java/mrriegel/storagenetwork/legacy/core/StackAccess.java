package mrriegel.storagenetwork.legacy.core;

public interface StackAccess<S> {
    boolean empty(S stack);
    int count(S stack);
    int limit(S stack);
    S copy(S stack, int count);
    boolean same(S left, S right);
}
