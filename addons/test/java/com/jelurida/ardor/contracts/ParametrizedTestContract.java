package com.jelurida.ardor.contracts;

import nxt.addons.AbstractContract;
import nxt.addons.ContractParametersProvider;
import nxt.addons.ContractSetupParameter;
import nxt.addons.JO;
import nxt.addons.RequestContext;

public class ParametrizedTestContract extends AbstractContract<Void,Void> {
    @ContractParametersProvider
    public interface Params {
        @ContractSetupParameter
        String str();

        @ContractSetupParameter
        JO obj();
    }

    @Override
    public JO processRequest(RequestContext context) {
        JO resp = new JO();
        Params params = context.getParams(Params.class);
        resp.put("str", params.str());
        resp.put("obj", params.obj());
        return context.generateResponse(resp);
    }
}
