package io.nosqlbench.virtdata.core.templates;

import java.util.Objects;

/**
 * <P>A bind point is a named injection point in a field value or
 * statement needed for an operation of some type. A bind point can
 * be specified as either a named reference to a binding recipe specified
 * elsewhere (a binding reference), or it can contain its own binding
 * recipe (a binding definition).
 * </P>
 *
 * <P>BindPoint references take the form of <pre>{@code {name}}</pre>
 * and only allow a simple binding name with no spaces, whereas
 * BindPoint definitions take the form of
 * <pre>{@code {{NumberNameToString()}}}</pre>
 * and allow any characters which do not match the closing double braces.
 *
 * </P>
 */
public class BindPoint {

    private final String anchor;
    private final String bindspec;
    private final Type type;


    /**
     * Type type of bindpoint indicates whether is was specified as a reference or a definition.
     */
    public enum Type {
        /**
         * a reference bindpoint is expressed as a single word within single curly braces like <pre>{@code {bindref}}</pre>
         */
        reference,
        /**
         * a definition bindpoint is expressed as anything between double curly braces like <pre>{@code {{Identity()}}</pre>
         */
        definition
    }

    public BindPoint(String anchor, String bindspec, Type type) {
        this.anchor = anchor;
        this.bindspec = bindspec;
        this.type = type;
    }

    public BindPoint(String anchor, String bindspec) {
        this.anchor = anchor;
        this.bindspec = bindspec;
        this.type = Type.reference;
    }

    public static BindPoint of(String userid, String bindspec, Type type) {
        return new BindPoint(userid, bindspec, type);
    }

    /**
     * The type of a bind point determines how it should be processed. {@link BindPoint.Type#definition} instances
     * should be taken as binding recipes to be evaluated directly. {@link BindPoint.Type#reference} instances
     * should be looked up in an externally supplied set of named bindings. In practical terms, these can be processed
     * identically so long as the bindspec is populated.
     * @return A binding type, from {@link BindPoint.Type}
     */
    public Type getType() {
        return type;
    }

    /**
     * The name of the anchor for a binding as it appears in a user-specified template, parsed by {@link ParsedTemplate}.
     * @return A string name for the bind point anchor, or null for {@link BindPoint.Type#definition} types.
     */
    public String getAnchor() {
        return anchor;
    }

    /**
     * @return The Binding specification. This is the binding recipe as it appears either in the binding directly
     * for {@link BindPoint.Type#definition} types, or as back-filled by a parser for {@link BindPoint.Type#reference} types.
     */
    public String getBindspec() {
        return bindspec;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BindPoint bindPoint = (BindPoint) o;
        return Objects.equals(anchor, bindPoint.anchor) && Objects.equals(bindspec, bindPoint.bindspec) && type == bindPoint.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(anchor, bindspec, type);
    }

    @Override
    public String toString() {
        return "BindPoint{" +
            "anchor='" + anchor + '\'' +
            ", bindspec='" + bindspec + '\'' +
            ", type=" + type +
            '}';
    }
}
