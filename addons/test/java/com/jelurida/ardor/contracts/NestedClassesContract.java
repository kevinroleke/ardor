/*
 * Copyright © 2016-2023 Jelurida IP B.V.
 * Copyright © 2023-2024 Jelurida Swiss SA
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

package com.jelurida.ardor.contracts;

import nxt.addons.AbstractContract;
import nxt.addons.JO;
import nxt.addons.RequestContext;
import nxt.util.JSON;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

public class NestedClassesContract extends AbstractContract<Void, Void> {
    private interface SerDe {
        JO serialize(MyData data);
        void deserialize(JO json, MyData result);
    }
    
    private enum Type {
        A, B
    }

    private class MyData {
        private Type a;
        private MyData(JO json) {
            serDe.deserialize(json, this);
        }
        public Type getType() {
            return a;
        }
    }

    private final SerDe serDe = new SerDe() {
        @Override
        public void deserialize(JO json, MyData result) {
            result.a = Type.valueOf(json.getString("type"));
        }

        @Override
        public JO serialize(MyData data) {
            JO result = new JO();
            switch (data.getType()) {
                case A:
                    result.put("type", "A");
                    break;
                case B:
                    result.put("type", "B");
                    break;
            }
            return result;
        }
    };

    @Override
    public JO processRequest(RequestContext context) {
        Object json = null;
        try {
            json = JSONValue.parseWithException(context.getParameter("json"));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        JSONArray list = new JO(json).getJoList("list").
                stream().map(MyData::new).map(serDe::serialize).
                collect(JSON.jsonArrayCollector());
        JO output = new JO();
        output.put("list", list);
        return context.generateResponse(output);
    }
}
