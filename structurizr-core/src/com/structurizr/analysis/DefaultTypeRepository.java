package com.structurizr.analysis;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This is an implementation of a TypeRepository that uses a combination of:
 *  - The Reflections library (https://github.com/ronmamo/reflections).
 *  - Javassist (http://jboss-javassist.github.io/javassist/)
 *  - Java Reflection
 */
public class DefaultTypeRepository implements TypeRepository {

    private static final Log log = LogFactory.getLog(DefaultTypeRepository.class);

    private final Set<Class<?>> types;

    private String packageToScan;
    private Set<Pattern> exclusions = new HashSet<>();

    private ClassPool classPool = ClassPool.getDefault();
    private Map<String, Set<Class<?>>> referencedTypesCache = new HashMap<>();

    /**
     * Creates a new instance based upon a package to scan and a set of exclusions.
     *
     * @param packageToScan     the fully qualified package name
     * @param exclusions        a Set of Pattern objects
     */
    public DefaultTypeRepository(String packageToScan, Set<Pattern> exclusions) {
        this.packageToScan = packageToScan;
        if (exclusions != null) {
            this.exclusions.addAll(exclusions);
        }

        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forJavaClassPath())
                .filterInputsBy(new FilterBuilder().includePackage(packageToScan))
                .setScanners(new SubTypesScanner(false))
        );

        types = filter(reflections.getSubTypesOf(Object.class));
    }

    /**
     * Gets the package that this type repository is associated with scanning.
     *
     * @return  a fully qualified package name
     */
    public String getPackage() {
        return packageToScan;
    }

    /**
     * Gets all of the types found by this type repository.
     *
     * @return  a Set of Class<?> objects, or an empty set of no classes were found
     */
    public Set<Class<?>> getAllTypes() {
        return new HashSet<>(types);
    }

    /**
     * Finds the set of types referenced by the specified type.
     *
     * @param typeName  the starting type
     * @return          a Set of Class<?> objects, or an empty set if none were found
     */
    public Set<Class<?>> findReferencedTypes(String typeName) throws Exception {
        Set<Class<?>> referencedTypes = new HashSet<>();

        // use the cached version if possible
        if (referencedTypesCache.containsKey(typeName)) {
            return referencedTypesCache.get(typeName);
        }

        try {
            CtClass cc = classPool.get(typeName);
            for (Object referencedType : cc.getRefClasses()) {
                String referencedTypeName = (String)referencedType;

                if (!isExcluded(referencedTypeName)) {
                    referencedTypes.add(ClassLoader.getSystemClassLoader().loadClass(referencedTypeName));
                }
            }

            // remove the type itself
            referencedTypes.remove(ClassLoader.getSystemClassLoader().loadClass(typeName));
        } catch (NotFoundException|ClassNotFoundException e) {
            log.debug("Could not find " + typeName + " ... ignoring.");

            // since the class could not be loaded, we can't find the set of referenced types from it, so...
            referencedTypesCache.put(typeName, new HashSet<>());
        }

        // cache for the next time
        referencedTypesCache.put(typeName, referencedTypes);

        return referencedTypes;
    }

    private Set<Class<?>> filter(Set<Class<?>> types) {
        return types.stream().filter(c -> !isExcluded(c.getCanonicalName())).collect(Collectors.toSet());
    }

    private boolean isExcluded(String typeName) {
        for (Pattern exclude : exclusions) {
            if (exclude.matcher(typeName).matches()) {
                return true;
            }
        }

        return false;
    }

}