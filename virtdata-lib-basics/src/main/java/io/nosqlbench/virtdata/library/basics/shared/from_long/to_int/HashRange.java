package io.nosqlbench.virtdata.library.basics.shared.from_long.to_int;

import io.nosqlbench.nb.api.errors.BasicError;
import io.nosqlbench.virtdata.api.annotations.Categories;
import io.nosqlbench.virtdata.api.annotations.Category;
import io.nosqlbench.virtdata.api.annotations.Example;
import io.nosqlbench.virtdata.api.annotations.ThreadSafeMapper;
import io.nosqlbench.virtdata.library.basics.shared.from_long.to_long.Hash;

import java.util.function.LongToIntFunction;

@ThreadSafeMapper
@Categories({Category.general})
public class HashRange implements LongToIntFunction {

    private final long minValue;
    private final long width;
    private final Hash hash = new Hash();

    @Example({"HashRange(32L)","map the input to a number in the range 0-31, inclusive, of type int"})
    public HashRange(int width) {
        this.width=width;
        this.minValue=0L;
    }

    @Example({"HashRange(35L,39L)","map the input to a number in the range 35-38, inclusive, of type int"})
    public HashRange(int minValue, int maxValue) {
        if (maxValue<minValue) {
            throw new BasicError("HashRange must have min and max value in that order.");
        }
        this.minValue = minValue;
        this.width = (maxValue - minValue) +1;
    }

    @Override
    public int applyAsInt(long operand) {
        return (int) ((minValue + (hash.applyAsLong(operand) % width)) & Integer.MAX_VALUE);
    }
}
