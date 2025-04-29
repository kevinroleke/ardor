/*
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2025 Jelurida Swiss SA
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida
 * Swiss SA, no part of this software, including this file, may be copied,
 * modified, propagated, or distributed except according to the terms
 * contained in the LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package nxt.addons;

import nxt.util.Convert;
import nxt.util.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.Reader;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Delegate json object operations to json simple and wrap it with convenience methods
 * This class does not really keep a map, but it implements a map in order to delegate entrySet to the underlying JSONArray
 * in order to support streaming into String.
 */
public class JO extends AbstractMap {

    public static class Builder {
        private final Map<String, Object> attributes = new HashMap<>();

        public JO.Builder put(String key, Object value) {
            attributes.put(key, value);
            return this;
        }

        public JO.Builder putAttributes(Map<String, Object> attributes) {
            attributes.forEach(this.attributes::put);
            return this;
        }

        public JO build() {
            JO jo = new JO();
            attributes.forEach(jo::put);
            return jo;
        }
    }

    private final JSONObject jo;

    public JO() {
        this.jo = new JSONObject();
    }

    public JO(JSONObject jo) {
        if (jo == null) {
            throw new IllegalArgumentException("Attempt to initialize JO with null JSONObject");
        }
        this.jo = jo;
    }

    public JO(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("Attempt to initialize JO with null Object");
        }
        if (obj instanceof JSONObject) {
            this.jo = (JSONObject)obj;
        } else {
            this.jo = ((JO)obj).toJSONObject();
        }
    }

    public JSONObject toJSONObject() {
        return jo;
    }

    public void put(String key, Object o) {
        jo.put(key, o);
    }

    @Override
    public Object put(Object key, Object o) {
        return jo.put(key, o);
    }

    public JA getArray(String key) {
        Object o = jo.get(key);
        if (o == null) {
            return new JA(new JSONArray()); // no need to deal with null checks
        }
        if (o instanceof JA) {
            return (JA)o;
        }
        return new JA((JSONArray) o);
    }

    public List<JO> getJoList(String key) {
        Object o = jo.get(key);
        if (o == null) {
            return Collections.EMPTY_LIST; // no need to deal with null checks
        } else if (o instanceof JSONArray) {
            return (List<JO>)(new JA((JSONArray) o));
        } else if(o instanceof JA) {
            return (List<JO>)(o);
        } else {
            throw new IllegalArgumentException(key);
        }
    }

    public static JO valueOf(Object o) {
        return new JO((JSONObject)o);
    }

    public static JO valueOf(JO o) {
        return o;
    }

    public static JO parse(String s) {
        try {
            return new JO(JSONValue.parseWithException(s));
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static JO parse(Reader r) {
        try {
            return new JO(JSONValue.parseWithException(r));
        } catch (ParseException | IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Object get(String key) {
        return jo.get(key);
    }

    public JO getJo(String key) {
        Object o = jo.get(key);
        if (o instanceof JSONObject) {
            return new JO(o);
        }
        return (JO)o;
    }

    public long getEntityId(String key) {
        Object value = jo.get(key);
        if (value == null) {
            return 0;
        }
        return Long.parseUnsignedLong((String) value);
    }

    // Used by JSON encodeObject
    @Override
    public Set<Entry> entrySet() {
        return jo.entrySet();
    }

    public String toJSONString() {
        return JSON.toJSONString(jo);
    }

    public long getLong(String key, long defaultValue) {
        if (isExist(key)) {
            return getLong(key);
        }
        return defaultValue;
    }

    public long getLong(String key) {
        Object value = jo.get(key);
        if (value instanceof String) {
            return Long.parseLong((String)value);
        }
        return (long)value;
    }

    public int getInt(String key, int defaultValue) {
        if (isExist(key)) {
            return getInt(key);
        }
        return defaultValue;
    }

    public int getInt(String key) {
        Object value = jo.get(key);
        if (value instanceof Integer) {
            return (int)value;
        }
        return (int)getLong(key);
    }

    public double getDouble(String key, double defaultValue) {
        if (isExist(key)) {
            return getDouble(key);
        }
        return defaultValue;
    }

    public double getDouble(String key) {
        Object value = jo.get(key);
        if (value instanceof String) {
            return Double.parseDouble((String)value);
        }
        if (value instanceof Long) {
            return ((Long)value).doubleValue();
        }
        return (double)value;
    }

    public float getFloat(String key, float defaultValue) {
        if (isExist(key)) {
            return getFloat(key);
        }
        return defaultValue;
    }

    public float getFloat(String key) {
        Object value = jo.get(key);
        if (value instanceof String) {
            return Float.parseFloat((String)value);
        }
        if (value instanceof Long) {
            return ((Long)value).floatValue();
        }
        return (float)value;
    }

    public short getShort(String key, short defaultValue) {
        if (isExist(key)) {
            return getShort(key);
        }
        return defaultValue;
    }

    public short getShort(String key) {
        Object value = jo.get(key);
        if (value instanceof Short) {
            return (short)value;
        }
        return (short)getLong(key);
    }

    public byte getByte(String key, byte defaultValue) {
        if (isExist(key)) {
            return getByte(key);
        }
        return defaultValue;
    }

    public byte getByte(String key) {
        Object value = jo.get(key);
        if (value instanceof Byte) {
            return (byte)value;
        }
        return (byte)getLong(key);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        if (isExist(key)) {
            return getBoolean(key);
        }
        return defaultValue;
    }

    public boolean getBoolean(String key) {
        Object o = jo.get(key);
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean) {
            return (boolean)o;
        }
        return Boolean.parseBoolean((String)o);
    }

    public String getString(String key, String defaultValue) {
        if (isExist(key)) {
            return getString(key);
        }
        return defaultValue;
    }

    public String getString(String key) {
        Object o = jo.get(key);
        if (o == null) {
            return null;
        }
        if (!(o instanceof String)) {
            return o.toString();
        }
        return (String)o;
    }

    public byte[] parseHexString(String key) {
        Object o = jo.get(key);
        if (o == null) {
            return null;
        }
        return Convert.parseHexString((String)o);
    }

    public boolean isExist(String key) {
        return jo.get(key) != null;
    }

    /**
     * Returns a non-deep copy of the provided JO.
     * @param jo JSON object
     * @return Copied object
     */
    public static JO copy(JO jo) {
        if (jo == null) {
            return null;
        }
        return new JO(new JSONObject(jo.jo));
    }

    /**
     * Returns an unmodifiable version of a JSON object. The result is not deeply unmodifiable - sub-objects or
     * sub-arrays can be modified.
     *
     * @param jo The {@link JO} to make unmodifiable
     *
     * @return An unmodifiable JO with same content as the provided JO
     */
    public static JO unmodifiable(JO jo) {
        return new UnmodifiableJO(jo);
    }

    private static class UnmodifiableJO extends JO {
        private UnmodifiableJO(JO jo) {
            super(jo);
        }

        @Override
        public Set<Entry> entrySet() {
            return Collections.unmodifiableSet(super.entrySet());
        }

        @Override
        public List<JO> getJoList(String key) {
            return Collections.unmodifiableList(super.getJoList(key));
        }

        @Override
        public JA getArray(String key) {
            throw new UnsupportedOperationException("use the getJoList() method instead");
        }

        @Override
        public Object put(Object key, Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void put(String key, Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JSONObject toJSONObject() {
            return new JSONObject(super.toJSONObject());
        }
    }
}
