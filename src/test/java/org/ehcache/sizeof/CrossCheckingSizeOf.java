package org.ehcache.sizeof;

import org.ehcache.sizeof.filters.SizeOfFilter;
import org.ehcache.sizeof.impl.AgentSizeOf;
import org.ehcache.sizeof.impl.PassThroughFilter;
import org.ehcache.sizeof.impl.ReflectionSizeOf;
import org.ehcache.sizeof.impl.UnsafeSizeOf;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import static org.ehcache.sizeof.impl.JvmInformation.CURRENT_JVM_INFORMATION;

/**
 * @author Alex Snaps
 */
public class CrossCheckingSizeOf extends SizeOf {

    private final List<SizeOf> engines;

    public CrossCheckingSizeOf() {
        this(new PassThroughFilter());
    }

    public CrossCheckingSizeOf(SizeOfFilter filter) {
        this(filter, true);
    }

    public CrossCheckingSizeOf(SizeOfFilter filter, boolean caching) {
        super(filter, caching);
        engines = new ArrayList<SizeOf>();

        try {
            engines.add(new AgentSizeOf());
        } catch (UnsupportedOperationException usoe) {
            System.err.println("Not using AgentSizeOf: " + usoe);
        }
        try {
            engines.add(new UnsafeSizeOf());
        } catch (UnsupportedOperationException usoe) {
            System.err.println("Not using UnsafeSizeOf: " + usoe);
        }
        if (CURRENT_JVM_INFORMATION.supportsReflectionSizeOf()) {
            try {
                engines.add(new ReflectionSizeOf());
            } catch (UnsupportedOperationException usoe) {
                System.err.println("Not using ReflectionSizeOf: " + usoe);
            }
        } else {
            System.err.println(CURRENT_JVM_INFORMATION.getJvmDescription() + " detected: not using ReflectionSizeOf");
        }

        if (engines.isEmpty()) {
            throw new AssertionError("No SizeOf engines available");
        }
    }

    @Override
    public long sizeOf(Object obj) {
        long[] values = new long[engines.size()];
        for (int i = 0; i < engines.size(); i++) {
            values[i] = engines.get(i).sizeOf(obj);
        }
        for (long value : values) {
            if (values[0] != value) {
                StringBuilder sb = new StringBuilder("Values do not match for ");
                sb.append(obj.getClass());
                if (obj.getClass().isArray()) {
                    sb.append(" length:").append(Array.getLength(obj));
                }
                sb.append(" - ");
                for (int i = 0; i < engines.size(); i++) {
                    sb.append(engines.get(i).getClass().getSimpleName()).append(":").append(values[i]);
                    if (i != engines.size() - 1) {
                        sb.append(", ");
                    }
                }
                throw new AssertionError(sb.toString());
            }
        }

        return values[0];
    }
}
