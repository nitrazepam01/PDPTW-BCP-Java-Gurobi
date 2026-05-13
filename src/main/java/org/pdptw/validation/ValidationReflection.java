package org.pdptw.validation;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ValidationReflection {
    private ValidationReflection() {
    }

    static int nRequests(Object instance) {
        return asInt(requiredProperty(instance, "nRequests", "getNRequests", "numberOfRequests"));
    }

    static int startDepotId(Object instance) {
        return asInt(requiredProperty(instance, "startDepotId", "originDepotId", "getStartDepotId"));
    }

    static int endDepotId(Object instance) {
        return asInt(requiredProperty(instance, "endDepotId", "destinationDepotId", "getEndDepotId"));
    }

    static int vehicleCapacity(Object instance) {
        return asInt(requiredProperty(instance, "vehicleCapacity", "capacity", "getVehicleCapacity"));
    }

    static int maxVehicles(Object instance) {
        return asInt(requiredProperty(instance, "maxVehicles", "getMaxVehicles"));
    }

    static double fleetDual(Object instance) {
        Object value = optionalProperty(instance, "fleetDual", "getFleetDual");
        return value == null ? 0.0 : asDouble(value);
    }

    @SuppressWarnings("unchecked")
    static Map<?, ?> requestDuals(Object instance) {
        Object value = optionalProperty(instance, "requestDuals", "getRequestDuals");
        if (value == null) {
            return Collections.emptyMap();
        }
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("requestDuals() must return a Map, got " + value.getClass().getName());
        }
        return (Map<?, ?>) value;
    }

    static double requestDual(Object instance, int requestId) {
        Map<?, ?> duals = requestDuals(instance);
        for (Map.Entry<?, ?> entry : duals.entrySet()) {
            if (keyAsRequestId(entry.getKey()) == requestId) {
                return asDouble(entry.getValue());
            }
        }
        return 0.0;
    }

    static double travelCost(Object instance, int from, int to) {
        return asDouble(requiredTwoIntCall(instance, "travelCost", from, to));
    }

    static double travelTime(Object instance, int from, int to) {
        return asDouble(requiredTwoIntCall(instance, "travelTime", from, to));
    }

    static Object vertex(Object instance, int id) {
        return optionalOneIntCall(instance, "vertex", id);
    }

    static double vertexReady(Object instance, int id) {
        Object vertex = vertex(instance, id);
        if (vertex == null) {
            return 0.0;
        }
        Object value = optionalProperty(vertex, "ready", "readyTime", "earliest", "timeWindowStart", "getReady");
        return value == null ? 0.0 : asDouble(value);
    }

    static double vertexDue(Object instance, int id) {
        Object vertex = vertex(instance, id);
        if (vertex == null) {
            return Double.POSITIVE_INFINITY;
        }
        Object value = optionalProperty(vertex, "due", "dueTime", "latest", "timeWindowEnd", "getDue");
        return value == null ? Double.POSITIVE_INFINITY : asDouble(value);
    }

    static double vertexService(Object instance, int id) {
        Object vertex = vertex(instance, id);
        if (vertex == null) {
            return 0.0;
        }
        Object value = optionalProperty(vertex, "service", "serviceTime", "getService");
        return value == null ? 0.0 : asDouble(value);
    }

    static double vertexDemand(Object instance, int id, int nRequests) {
        Object vertex = vertex(instance, id);
        if (vertex != null) {
            Object value = optionalProperty(vertex, "demand", "load", "getDemand");
            if (value != null) {
                return asDouble(value);
            }
        }
        if (isPickup(id, nRequests)) {
            return 1.0;
        }
        if (isDelivery(id, nRequests)) {
            return -1.0;
        }
        return 0.0;
    }

    static List<Integer> routeVertexIds(Object route) {
        if (route instanceof List<?>) {
            return asIntList(route);
        }
        if (route instanceof Collection<?>) {
            return asIntList(route);
        }
        if (route != null && route.getClass().isArray()) {
            return asIntList(route);
        }
        Object ids = optionalProperty(route, "vertexIds", "vertices", "ids", "getVertexIds");
        if (ids == null) {
            throw new IllegalArgumentException("Route must be a vertex id list or expose vertexIds()");
        }
        return asIntList(ids);
    }

    static double routeCost(Object instance, Object route) {
        if (!(route instanceof List<?>) && !(route instanceof Collection<?>) && (route == null || !route.getClass().isArray())) {
            Object cost = optionalOneObjectCall(route, "cost", instance);
            if (cost != null) {
                return asDouble(cost);
            }
        }
        return routeCostFromIds(instance, routeVertexIds(route));
    }

    static double routeCostFromIds(Object instance, List<Integer> ids) {
        double cost = 0.0;
        for (int i = 0; i + 1 < ids.size(); i++) {
            cost += travelCost(instance, ids.get(i), ids.get(i + 1));
        }
        return cost;
    }

    static Set<Integer> servedRequests(Object instance, Object route) {
        if (!(route instanceof List<?>) && !(route instanceof Collection<?>) && (route == null || !route.getClass().isArray())) {
            Object served = optionalOneObjectCall(route, "servedRequests", instance);
            if (served != null) {
                return new LinkedHashSet<Integer>(asIntList(served));
            }
        }
        return servedRequestsFromIds(instance, routeVertexIds(route));
    }

    static Set<Integer> servedRequestsFromIds(Object instance, List<Integer> ids) {
        int n = nRequests(instance);
        boolean[] seenPickup = new boolean[n + 1];
        boolean[] seenDelivery = new boolean[n + 1];
        for (Integer idValue : ids) {
            int id = idValue.intValue();
            if (isPickup(id, n)) {
                seenPickup[id] = true;
            } else if (isDelivery(id, n)) {
                seenDelivery[id - n] = true;
            }
        }
        LinkedHashSet<Integer> served = new LinkedHashSet<Integer>();
        for (int request = 1; request <= n; request++) {
            if (seenPickup[request] && seenDelivery[request]) {
                served.add(request);
            }
        }
        return served;
    }

    static boolean isPickup(int vertexId, int nRequests) {
        return vertexId >= 1 && vertexId <= nRequests;
    }

    static boolean isDelivery(int vertexId, int nRequests) {
        return vertexId > nRequests && vertexId <= 2 * nRequests;
    }

    static int requestOf(int vertexId, int nRequests) {
        if (isPickup(vertexId, nRequests)) {
            return vertexId;
        }
        if (isDelivery(vertexId, nRequests)) {
            return vertexId - nRequests;
        }
        return 0;
    }

    static Object newCoreRouteOrNull(List<Integer> ids) {
        try {
            Class<?> routeClass = Class.forName("org.pdptw.core.Route");
            Object route = constructWithSingleArg(routeClass, new ArrayList<Integer>(ids));
            if (route != null) {
                return route;
            }
            int[] primitiveIds = new int[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                primitiveIds[i] = ids.get(i).intValue();
            }
            route = constructWithSingleArg(routeClass, primitiveIds);
            if (route != null) {
                return route;
            }
            for (Method method : allMethods(routeClass)) {
                if (method.getName().equals("of") && method.getParameterTypes().length == 1) {
                    trySetAccessible(method);
                    Class<?> parameter = method.getParameterTypes()[0];
                    if (parameter.isAssignableFrom(List.class) || parameter.isAssignableFrom(ArrayList.class)) {
                        return method.invoke(null, new ArrayList<Integer>(ids));
                    }
                    if (parameter.isArray() && parameter.getComponentType() == Integer.TYPE) {
                        return method.invoke(null, primitiveIds);
                    }
                }
            }
            return null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Object constructWithSingleArg(Class<?> type, Object arg) {
        for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterTypes().length != 1) {
                continue;
            }
            Class<?> parameter = constructor.getParameterTypes()[0];
            if (!isAssignableArgument(parameter, arg)) {
                continue;
            }
            try {
                trySetAccessible(constructor);
                return constructor.newInstance(arg);
            } catch (ReflectiveOperationException exception) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private static Object requiredTwoIntCall(Object target, String name, int first, int second) {
        Object value = optionalTwoIntCall(target, name, first, second);
        if (value == null) {
            throw new IllegalArgumentException("Missing method " + name + "(int,int) on " + className(target));
        }
        return value;
    }

    private static Object optionalTwoIntCall(Object target, String name, int first, int second) {
        if (target == null) {
            return null;
        }
        for (Method method : allMethods(target.getClass())) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != 2) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (!canAcceptInt(parameters[0]) || !canAcceptInt(parameters[1])) {
                continue;
            }
            try {
                trySetAccessible(method);
                return method.invoke(target, Integer.valueOf(first), Integer.valueOf(second));
            } catch (ReflectiveOperationException exception) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private static Object optionalOneIntCall(Object target, String name, int value) {
        if (target == null) {
            return null;
        }
        for (Method method : allMethods(target.getClass())) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != 1) {
                continue;
            }
            if (!canAcceptInt(method.getParameterTypes()[0])) {
                continue;
            }
            try {
                trySetAccessible(method);
                return method.invoke(target, Integer.valueOf(value));
            } catch (ReflectiveOperationException exception) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private static Object optionalOneObjectCall(Object target, String name, Object arg) {
        if (target == null) {
            return null;
        }
        for (Method method : allMethods(target.getClass())) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != 1) {
                continue;
            }
            if (!isAssignableArgument(method.getParameterTypes()[0], arg)) {
                continue;
            }
            try {
                trySetAccessible(method);
                return method.invoke(target, arg);
            } catch (ReflectiveOperationException exception) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private static Object requiredProperty(Object target, String... names) {
        Object value = optionalProperty(target, names);
        if (value == null) {
            throw new IllegalArgumentException("Missing property/method on " + className(target) + ": " + join(names));
        }
        return value;
    }

    private static Object optionalProperty(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            Method method = findNoArgMethod(target.getClass(), name);
            if (method != null) {
                try {
                    trySetAccessible(method);
                    return method.invoke(target);
                } catch (ReflectiveOperationException exception) {
                    // Try field fallback with the same name.
                }
            }
            Field field = findField(target.getClass(), name);
            if (field != null) {
                try {
                    trySetAccessible(field);
                    return field.get(target);
                } catch (ReflectiveOperationException exception) {
                    // Try the next name.
                }
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        for (Method method : allMethods(type)) {
            if (method.getName().equals(name) && method.getParameterTypes().length == 0) {
                return method;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getDeclaredField(name);
            } catch (NoSuchFieldException exception) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    private static List<Method> allMethods(Class<?> type) {
        ArrayList<Method> methods = new ArrayList<Method>();
        Class<?> cursor = type;
        while (cursor != null) {
            Method[] declaredMethods = cursor.getDeclaredMethods();
            for (Method method : declaredMethods) {
                methods.add(method);
            }
            cursor = cursor.getSuperclass();
        }
        Method[] publicMethods = type.getMethods();
        for (Method method : publicMethods) {
            methods.add(method);
        }
        return methods;
    }

    private static void trySetAccessible(java.lang.reflect.AccessibleObject object) {
        try {
            object.setAccessible(true);
        } catch (SecurityException exception) {
            // Public members will still be invokable.
        }
    }

    private static boolean canAcceptInt(Class<?> parameter) {
        return parameter == Integer.TYPE
                || parameter == Integer.class
                || parameter == Long.TYPE
                || parameter == Long.class
                || parameter == Number.class
                || parameter == Object.class;
    }

    private static boolean isAssignableArgument(Class<?> parameter, Object arg) {
        if (arg == null) {
            return !parameter.isPrimitive();
        }
        if (parameter.isPrimitive()) {
            if (parameter == Integer.TYPE) {
                return arg instanceof Integer;
            }
            if (parameter == Long.TYPE) {
                return arg instanceof Long || arg instanceof Integer;
            }
            if (parameter == Double.TYPE) {
                return arg instanceof Double || arg instanceof Float || arg instanceof Integer || arg instanceof Long;
            }
            return false;
        }
        return parameter.isAssignableFrom(arg.getClass());
    }

    private static List<Integer> asIntList(Object value) {
        ArrayList<Integer> ids = new ArrayList<Integer>();
        if (value == null) {
            return ids;
        }
        if (value instanceof Iterable<?>) {
            for (Object element : (Iterable<?>) value) {
                ids.add(Integer.valueOf(asInt(element)));
            }
            return ids;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                ids.add(Integer.valueOf(asInt(Array.get(value, i))));
            }
            return ids;
        }
        throw new IllegalArgumentException("Cannot convert to integer list: " + type.getName());
    }

    private static int keyAsRequestId(Object key) {
        if (key instanceof Number) {
            return ((Number) key).intValue();
        }
        return Integer.parseInt(String.valueOf(key));
    }

    private static int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double asDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static String join(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(values[i]);
        }
        return builder.toString();
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
