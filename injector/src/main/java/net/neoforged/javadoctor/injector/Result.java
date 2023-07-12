package net.neoforged.javadoctor.injector;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class Result<T> {
    private final T result;
    private final List<String> problems;

    public Result(T result) {
        this.result = result;
        this.problems = null;
    }

    public Result(List<String> problems) {
        this.result = null;
        this.problems = problems;
    }

    public Optional<T> getResult() {
        return Optional.ofNullable(result);
    }

    public <Z> Result<Z> map(Function<T, Z> mapper) {
        if (result == null) {
            return new Result<>(problems);
        } else {
            return new Result<>(mapper.apply(result));
        }
    }

    public List<String> getProblems() {
        return problems == null ? Collections.emptyList() : problems;
    }
}
