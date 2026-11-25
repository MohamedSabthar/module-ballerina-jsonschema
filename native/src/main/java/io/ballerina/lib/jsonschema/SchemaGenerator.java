/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.jsonschema;

import io.ballerina.runtime.api.Module;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.AnnotatableType;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.FiniteType;
import io.ballerina.runtime.api.types.IntersectionType;
import io.ballerina.runtime.api.types.MapType;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.ReferenceType;
import io.ballerina.runtime.api.types.TableType;
import io.ballerina.runtime.api.types.TupleType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.TypeTags;
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static io.ballerina.lib.jsonschema.Utils.FIELD_ANNOT_PREFIX;
import static io.ballerina.lib.jsonschema.Utils.JSONSCHEMA_ANNOT_PREFIX;
import static io.ballerina.lib.jsonschema.Utils.isAnnotation;

/**
 * Generates a JSON Schema (draft 2020-12) from a Ballerina runtime type.
 *
 * @since 0.1.0
 */
public final class SchemaGenerator {
    static final String SCHEMA_URI = "https://json-schema.org/draft/2020-12/schema";

    private static final String TYPE = "type";
    private static final String REF = "$ref";
    private static final String DEFS = "$defs";
    private static final String DESCRIPTION = "description";
    private static final String ANY_OF = "anyOf";
    /** Anonymous types have names like {@code $anonType$_0} or {@code record {| string code; |}}. */
    private static final Pattern IDENTIFIER = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]*");
    private static final String REGEXP_MODULE = "lang.regexp";

    private final boolean inlineRefs;
    /** Generated definitions, keyed by definition name. */
    private final Map<String, Object> defs = new LinkedHashMap<>();
    /** Unique type key (module + name) to definition name. */
    private final Map<String, String> defNames = new HashMap<>();
    /** Named types currently being generated (used to detect recursion). */
    private final Set<String> inProgress = new HashSet<>();
    /** Named types found to be recursive. */
    private final Set<String> recursive = new HashSet<>();
    private String rootKey;

    private SchemaGenerator(boolean inlineRefs) {
        this.inlineRefs = inlineRefs;
    }

    /**
     * Entry point called from Ballerina.
     *
     * @param typedesc         the type to generate a schema for
     * @param includeSchemaUri whether to emit the {@code $schema} keyword
     * @param inlineRefs       whether to inline named types instead of using {@code $defs}
     * @param pretty           whether to pretty print the JSON
     * @return the schema as a JSON string, or an error
     */
    public static Object generate(BTypedesc typedesc, boolean includeSchemaUri, boolean inlineRefs,
                                  boolean pretty) {
        try {
            Map<String, Object> schema = generateSchema(typedesc.getDescribingType(), includeSchemaUri, inlineRefs);
            return StringUtils.fromString(JsonWriter.write(schema, pretty));
        } catch (SchemaGenerationException e) {
            return ModuleUtils.createError(e.getMessage());
        }
    }

    static Map<String, Object> generateSchema(Type type, boolean includeSchemaUri, boolean inlineRefs) {
        SchemaGenerator generator = new SchemaGenerator(inlineRefs);
        Map<String, Object> body = generator.generateRoot(type);
        Map<String, Object> result = new LinkedHashMap<>();
        if (includeSchemaUri) {
            result.put("$schema", SCHEMA_URI);
        }
        result.putAll(body);
        if (!generator.defs.isEmpty()) {
            result.put(DEFS, generator.defs);
        }
        return result;
    }

    private Map<String, Object> generateRoot(Type type) {
        String key = namedKey(type);
        if (key == null) {
            return schemaFor(type);
        }
        rootKey = key;
        inProgress.add(key);
        return namedBody(type);
    }

    // ---------------------------------------------------------------- named types

    /**
     * Returns a unique key for a user-defined named type, or {@code null} if the type is anonymous.
     */
    private static String namedKey(Type type) {
        int tag = type.getTag();
        boolean nameable = tag == TypeTags.TYPE_REFERENCED_TYPE_TAG || tag == TypeTags.RECORD_TYPE_TAG
                || tag == TypeTags.UNION_TAG || tag == TypeTags.TUPLE_TAG;
        if (!nameable || !IDENTIFIER.matcher(type.getName()).matches()) {
            return null;
        }
        Module module = type.getPackage();
        return module.getOrg() + "/" + module.getName() + ":" + type.getName();
    }

    private String defName(String key, Type type) {
        return defNames.computeIfAbsent(key, k -> {
            String name = type.getName();
            // Types with the same name from different modules are prefixed with the module name.
            return defNames.containsValue(name) ? type.getPackage().getName().replace('.', '_') + "_" + name : name;
        });
    }

    private Map<String, Object> ref(String key, Type type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(REF, key.equals(rootKey) ? "#" : "#/" + DEFS + "/" + defName(key, type));
        return schema;
    }

    private Map<String, Object> schemaForNamed(Type type, String key) {
        if (inProgress.contains(key)) {
            recursive.add(key);
            return ref(key, type);
        }
        if (!inlineRefs && defs.containsKey(defNames.get(key))) {
            return ref(key, type);
        }
        inProgress.add(key);
        Map<String, Object> body = namedBody(type);
        inProgress.remove(key);
        if (inlineRefs && !recursive.contains(key)) {
            return body;
        }
        defs.put(defName(key, type), body);
        return ref(key, type);
    }

    /**
     * Builds the schema body of a named type, including its type-level annotations.
     */
    private Map<String, Object> namedBody(Type type) {
        // An alias of another named type (`type A B;`) refers to it.
        Type target = type.getTag() == TypeTags.TYPE_REFERENCED_TYPE_TAG ? ((ReferenceType) type).getReferredType()
                : null;
        Map<String, Object> structural = target == null ? buildStructural(type) : schemaFor(target, true);
        return withAnnotations(structural, typeAnnotations(type, target));
    }

    // ---------------------------------------------------------------- dispatch

    private Map<String, Object> schemaFor(Type type) {
        return schemaFor(type, false);
    }

    /**
     * Returns the schema of a type.
     *
     * @param type       the type
     * @param structural whether to build the structure of a named non-reference type (record, union, tuple) instead
     *                   of referring to it. Used when generating the body of a type reference.
     */
    private Map<String, Object> schemaFor(Type type, boolean structural) {
        if (type.getTag() == TypeTags.TYPE_REFERENCED_TYPE_TAG && type.getPackage().getName().startsWith("lang.")) {
            // Built-in types such as `int:Signed8` and `string:RegExp`.
            return REGEXP_MODULE.equals(type.getPackage().getName()) ? regexSchema()
                    : schemaFor(((ReferenceType) type).getReferredType());
        }
        // Type references (type definitions) are always named.
        String key = structural && type.getTag() != TypeTags.TYPE_REFERENCED_TYPE_TAG ? null : namedKey(type);
        return key != null ? schemaForNamed(type, key) : buildStructural(type);
    }

    private Map<String, Object> buildStructural(Type type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        switch (type.getTag()) {
            case TypeTags.INT_TAG -> schema.put(TYPE, "integer");
            case TypeTags.BYTE_TAG, TypeTags.UNSIGNED8_INT_TAG -> integerRange(schema, 0, 255);
            case TypeTags.SIGNED8_INT_TAG -> integerRange(schema, -128, 127);
            case TypeTags.SIGNED16_INT_TAG -> integerRange(schema, -32768, 32767);
            case TypeTags.UNSIGNED16_INT_TAG -> integerRange(schema, 0, 65535);
            case TypeTags.SIGNED32_INT_TAG -> integerRange(schema, Integer.MIN_VALUE, Integer.MAX_VALUE);
            case TypeTags.UNSIGNED32_INT_TAG -> integerRange(schema, 0, 4294967295L);
            case TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG -> schema.put(TYPE, "number");
            case TypeTags.STRING_TAG -> schema.put(TYPE, "string");
            case TypeTags.CHAR_STRING_TAG -> {
                schema.put(TYPE, "string");
                schema.put("minLength", 1L);
                schema.put("maxLength", 1L);
            }
            case TypeTags.BOOLEAN_TAG -> schema.put(TYPE, "boolean");
            case TypeTags.NULL_TAG -> schema.put(TYPE, "null");
            case TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG, TypeTags.READONLY_TAG -> {
                // Any JSON value.
            }
            case TypeTags.NEVER_TAG -> schema.put("not", new LinkedHashMap<>());
            case TypeTags.FINITE_TYPE_TAG -> finite((FiniteType) type, schema);
            case TypeTags.ARRAY_TAG -> array((ArrayType) type, schema);
            case TypeTags.TUPLE_TAG -> tuple((TupleType) type, schema);
            case TypeTags.MAP_TAG -> map((MapType) type, schema);
            case TypeTags.TABLE_TAG -> {
                schema.put(TYPE, "array");
                schema.put("items", at("[*]", () -> schemaFor(((TableType) type).getConstrainedType())));
            }
            case TypeTags.RECORD_TYPE_TAG -> record((RecordType) type, schema);
            case TypeTags.UNION_TAG -> union((UnionType) type, schema);
            case TypeTags.INTERSECTION_TAG -> {
                return intersection((IntersectionType) type);
            }
            // Only `xml` types are anydata but not representable in JSON: `xml`, `xml:Element`, ...
            default -> throw new SchemaGenerationException("unsupported type for JSON Schema generation: '"
                    + ("xml".equals(type.getName()) ? "xml" : "xml:" + type.getName()) + "'");
        }
        return schema;
    }

    // ---------------------------------------------------------------- type kinds

    /**
     * Builds the schema of a part of a type, adding the part's path (e.g. {@code .field}, {@code [*]}) to the
     * error if the part can't be represented.
     */
    private static Map<String, Object> at(String pathSegment, Supplier<Map<String, Object>> schema) {
        try {
            return schema.get();
        } catch (SchemaGenerationException e) {
            throw e.at(pathSegment);
        }
    }

    private static Map<String, Object> regexSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(TYPE, "string");
        schema.put("format", "regex");
        return schema;
    }

    private static void integerRange(Map<String, Object> schema, long min, long max) {
        schema.put(TYPE, "integer");
        schema.put("minimum", min);
        schema.put("maximum", max);
    }

    private static void finite(FiniteType type, Map<String, Object> schema) {
        List<Object> values = new ArrayList<>();
        for (Object value : type.getValueSpace()) {
            values.add(Utils.toNode(value));
        }
        if (values.size() == 1) {
            schema.put("const", values.get(0));
        } else {
            schema.put("enum", values);
        }
    }

    private void array(ArrayType type, Map<String, Object> schema) {
        schema.put(TYPE, "array");
        schema.put("items", at("[*]", () -> schemaFor(type.getElementType())));
        if (type.getState() == ArrayType.ArrayState.CLOSED) {
            schema.put("minItems", (long) type.getSize());
            schema.put("maxItems", (long) type.getSize());
        }
    }

    private void tuple(TupleType type, Map<String, Object> schema) {
        schema.put(TYPE, "array");
        List<Type> members = type.getTupleTypes();
        if (!members.isEmpty()) {
            List<Object> prefixItems = new ArrayList<>();
            for (int i = 0; i < members.size(); i++) {
                Type member = members.get(i);
                prefixItems.add(at("[" + i + "]", () -> schemaFor(member)));
            }
            schema.put("prefixItems", prefixItems);
        }
        Type rest = type.getRestType();
        schema.put("items", rest == null ? Boolean.FALSE : at("[*]", () -> schemaFor(rest)));
        if (!members.isEmpty()) {
            schema.put("minItems", (long) members.size());
        }
    }

    private void map(MapType type, Map<String, Object> schema) {
        schema.put(TYPE, "object");
        Type constraint = type.getConstrainedType();
        if (TypeUtils.getImpliedType(constraint).getTag() == TypeTags.NEVER_TAG) {
            // `map<never>` is always empty.
            schema.put("additionalProperties", Boolean.FALSE);
        } else if (!isAnyJson(constraint)) {
            schema.put("additionalProperties", at(".*", () -> schemaFor(constraint)));
        }
    }

    private void record(RecordType type, Map<String, Object> schema) {
        schema.put(TYPE, "object");
        BMap<BString, Object> annotations = type.getAnnotations();
        Map<String, Object> properties = new LinkedHashMap<>();
        List<Object> required = new ArrayList<>();
        for (Field field : type.getFields().values()) {
            String name = field.getFieldName();
            Type fieldType = field.getFieldType();
            if (TypeUtils.getImpliedType(fieldType).getTag() == TypeTags.NEVER_TAG) {
                // `never` fields can never be present.
                continue;
            }
            Map<String, Object> propertySchema = withAnnotations(at("." + name, () -> schemaFor(fieldType)),
                    fieldAnnotations(annotations, name));
            addDefault(fieldAnnotations(annotations, name), fieldType, propertySchema);
            if (SymbolFlags.isFlagOn(field.getFlags(), SymbolFlags.READONLY)) {
                propertySchema.put("readOnly", Boolean.TRUE);
            }
            properties.put(name, propertySchema);
            if (SymbolFlags.isFlagOn(field.getFlags(), SymbolFlags.REQUIRED)) {
                required.add(name);
            }
        }
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        if (type.isSealed() || TypeUtils.getImpliedType(type.getRestFieldType()).getTag() == TypeTags.NEVER_TAG) {
            schema.put("additionalProperties", Boolean.FALSE);
        } else if (!isAnyJson(type.getRestFieldType())) {
            schema.put("additionalProperties", at(".*", () -> schemaFor(type.getRestFieldType())));
        }
    }

    @SuppressWarnings("unchecked")
    private void union(UnionType type, Map<String, Object> schema) {
        boolean nullable = false;
        boolean allLiterals = true;
        List<Object> literals = new ArrayList<>();
        List<Type> members = new ArrayList<>();
        for (Type member : type.getOriginalMemberTypes()) {
            Type implied = TypeUtils.getImpliedType(member);
            if (implied.getTag() == TypeTags.NULL_TAG) {
                nullable = true;
                continue;
            }
            members.add(member);
            if (implied.getTag() == TypeTags.FINITE_TYPE_TAG) {
                for (Object value : ((FiniteType) implied).getValueSpace()) {
                    literals.add(Utils.toNode(value));
                }
            } else {
                allLiterals = false;
            }
        }
        if (members.isEmpty()) {
            // `()|()`
            schema.put(TYPE, "null");
            return;
        }
        if (allLiterals) {
            // A union of literals (e.g. an enum, or `"a"|"b"|()`).
            if (nullable) {
                literals.add(null);
            }
            schema.put("enum", literals);
            return;
        }
        List<Object> anyOf = new ArrayList<>();
        for (Type member : members) {
            Map<String, Object> memberSchema = schemaFor(member);
            // Flatten nested anonymous unions, e.g. `(int|string)|boolean`.
            List<?> parts = memberSchema.size() == 1 && memberSchema.get(ANY_OF) instanceof List<?> nested
                    ? nested : List.of(memberSchema);
            for (Object part : parts) {
                // Identical members (e.g. `float|decimal`, both numbers) are listed once.
                if (!anyOf.contains(part)) {
                    anyOf.add(part);
                }
            }
        }
        if (nullable) {
            anyOf.add(Map.of(TYPE, "null"));
        }
        if (anyOf.contains(Map.of())) {
            // A member accepts any value (e.g. `json|int`), so the union does too: `{}`.
            return;
        }
        if (anyOf.size() == 1) {
            schema.putAll((Map<String, Object>) anyOf.get(0));
            return;
        }
        schema.put(ANY_OF, anyOf);
    }

    private Map<String, Object> intersection(IntersectionType type) {
        // `readonly & T`: the schema of T. Named constituents are referred to rather than inlined.
        Type constituent = type.getConstituentTypes().get(0);
        Type other = type.getConstituentTypes().get(1);
        return schemaFor(constituent.getTag() == TypeTags.READONLY_TAG ? other : constituent);
    }

    private static boolean isAnyJson(Type type) {
        int tag = TypeUtils.getImpliedType(type).getTag();
        return tag == TypeTags.ANYDATA_TAG || tag == TypeTags.JSON_TAG;
    }

    // ---------------------------------------------------------------- annotations

    /**
     * Returns the type-level annotations of a type definition. They may live on the type reference and/or on the
     * referred type.
     */
    private static BMap<BString, Object> typeAnnotations(Type type, Type referred) {
        BMap<BString, Object> merged = ValueCreator.createMapValue();
        // An alias of another named type (`type A B;`) refers to B's schema, which has B's annotations already.
        if (referred != null && referred.getTag() != TypeTags.TYPE_REFERENCED_TYPE_TAG) {
            copyTypeAnnotations(referred, merged);
        }
        // The type's own annotations win over the referred type's (they have the same keys).
        copyTypeAnnotations(type, merged);
        return merged;
    }

    private static void copyTypeAnnotations(Type type, BMap<BString, Object> target) {
        if (type instanceof AnnotatableType annotatable) {
            BMap<BString, Object> annotations = annotatable.getAnnotations();
            for (BString key : annotations.getKeys()) {
                if (!key.getValue().startsWith(FIELD_ANNOT_PREFIX)) {
                    target.put(key, annotations.get(key));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static BMap<BString, Object> fieldAnnotations(BMap<BString, Object> recordAnnotations, String field) {
        Object value = Utils.get(recordAnnotations, FIELD_ANNOT_PREFIX + field);
        return value == null ? ValueCreator.createMapValue() : (BMap<BString, Object>) value;
    }

    /**
     * Returns a new schema with title/description first, then the structural keywords, then keywords derived from
     * constraint and {@code Meta} annotations.
     */
    private static Map<String, Object> withAnnotations(Map<String, Object> structural,
                                                       BMap<BString, Object> annotations) {
        BMap<?, ?> meta = null;
        String docDescription = null;
        for (BString bKey : annotations.getKeys()) {
            String key = bKey.getValue();
            if (isAnnotation(key, JSONSCHEMA_ANNOT_PREFIX, "Meta")) {
                meta = (BMap<?, ?>) annotations.get(bKey);
            } else if (isAnnotation(key, JSONSCHEMA_ANNOT_PREFIX, "Description")) {
                docDescription = StringUtils.getStringValue(Utils.get((BMap<?, ?>) annotations.get(bKey), "value"));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Object metaDescription = null;
        if (meta != null) {
            putIfPresent(meta, "title", result);
            metaDescription = Utils.get(meta, DESCRIPTION);
        }
        if (metaDescription != null) {
            result.put(DESCRIPTION, Utils.toNode(metaDescription));
        } else if (docDescription != null) {
            result.put(DESCRIPTION, docDescription);
        }
        // The structural schema may be an inlined named type with its own title/description: the ones given here
        // (e.g. on the record field) take precedence.
        structural.forEach(result::putIfAbsent);
        ConstraintMapper.apply(annotations, result);
        if (meta != null) {
            putIfPresent(meta, "format", result);
            putIfPresent(meta, "examples", result);
            putIfPresent(meta, "deprecated", result);
        }
        return result;
    }

    /**
     * Adds the {@code default} keyword from a {@code @jsonschema:Default} annotation, unless the value can't be
     * represented in JSON (e.g. {@code xml} or a non-finite float).
     */
    private static void addDefault(BMap<BString, Object> fieldAnnotations, Type fieldType,
                                   Map<String, Object> schema) {
        Object node = defaultNode(fieldAnnotations);
        if (node != Utils.NOT_JSON) {
            schema.put("default", completeDefault(node, fieldType));
        }
    }

    /**
     * Returns the JSON value of the {@code @jsonschema:Default} annotation, or {@link Utils#NOT_JSON} if there's
     * none or it can't be represented in JSON.
     */
    private static Object defaultNode(BMap<BString, Object> fieldAnnotations) {
        for (BString bKey : fieldAnnotations.getKeys()) {
            if (isAnnotation(bKey.getValue(), JSONSCHEMA_ANNOT_PREFIX, "Default")) {
                return Utils.jsonNode(Utils.get((BMap<?, ?>) fieldAnnotations.get(bKey), "value"));
            }
        }
        return Utils.NOT_JSON;
    }

    /**
     * Completes a default value with the defaults of nested record fields. A mapping constructor in an annotation
     * (e.g. {@code @Default {value: {host: "localhost"}}}) is a plain map, so the defaults of the record type it is
     * the default of (e.g. {@code int port = 8080;}) are missing. They are taken from the {@code @jsonschema:Default}
     * annotations of those fields; fields without one (e.g. a function call default) stay missing.
     */
    @SuppressWarnings("unchecked")
    private static Object completeDefault(Object node, Type type) {
        Type impliedType = TypeUtils.getImpliedType(type);
        if (node instanceof Map<?, ?> map && impliedType instanceof RecordType recordType) {
            Map<String, Object> mapping = (Map<String, Object>) map;
            BMap<BString, Object> annotations = recordType.getAnnotations();
            for (Field field : recordType.getFields().values()) {
                String name = field.getFieldName();
                Object value = mapping.containsKey(name) ? mapping.get(name)
                        : defaultNode(fieldAnnotations(annotations, name));
                if (value != Utils.NOT_JSON) {
                    mapping.put(name, completeDefault(value, field.getFieldType()));
                }
            }
        } else if (node instanceof List<?> list && impliedType instanceof ArrayType arrayType) {
            List<Object> members = (List<Object>) list;
            members.replaceAll(member -> completeDefault(member, arrayType.getElementType()));
        }
        return node;
    }

    private static void putIfPresent(BMap<?, ?> meta, String keyword, Map<String, Object> schema) {
        Object value = Utils.get(meta, keyword);
        if (value != null) {
            schema.put(keyword, Utils.toNode(value));
        }
    }
}
