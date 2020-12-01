// Auto generated code, do not modify
package nxt.http.callers;

public class GetAliasesLikeCall extends ChainSpecificCallBuilder<GetAliasesLikeCall> {
    private GetAliasesLikeCall() {
        super(ApiSpec.getAliasesLike);
    }

    public static GetAliasesLikeCall create() {
        return new GetAliasesLikeCall();
    }

    public static GetAliasesLikeCall create(int chain) {
        return new GetAliasesLikeCall().param("chain", chain);
    }

    public GetAliasesLikeCall requireLastBlock(String requireLastBlock) {
        return param("requireLastBlock", requireLastBlock);
    }

    public GetAliasesLikeCall firstIndex(int firstIndex) {
        return param("firstIndex", firstIndex);
    }

    public GetAliasesLikeCall lastIndex(int lastIndex) {
        return param("lastIndex", lastIndex);
    }

    public GetAliasesLikeCall requireBlock(String requireBlock) {
        return param("requireBlock", requireBlock);
    }

    public GetAliasesLikeCall adminPassword(String adminPassword) {
        return param("adminPassword", adminPassword);
    }

    public GetAliasesLikeCall aliasPrefix(String aliasPrefix) {
        return param("aliasPrefix", aliasPrefix);
    }
}
