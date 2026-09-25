package com.xinyihl.constructionwandlegacy.material.source;

/**
 * Identity check shared by volatile inventory endpoints without depending on optional APIs.
 */
public final class CapturedEndpointIdentity {
    private CapturedEndpointIdentity() {
    }

    public static boolean matches(Object capturedParent, Object currentParent, Object capturedOwner, Object currentOwner) {
        return capturedParent != null && capturedParent == currentParent && capturedOwner != null && capturedOwner == currentOwner;
    }
}
