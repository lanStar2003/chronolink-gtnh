package dev.chronolink.core;

import java.util.Objects;
import java.util.UUID;

/** A channel number is not a password: owner identity is always part of the key. */
public final class RouteKey {
    public final UUID owner;
    public final int channel;
    public final ResourceKind resource;
    public RouteKey(UUID owner,int channel,ResourceKind resource) {
        this.owner=Objects.requireNonNull(owner,"owner");
        if(channel<1 || channel>64) throw new IllegalArgumentException("channel");
        this.channel=channel;
        this.resource=Objects.requireNonNull(resource,"resource");
    }
    @Override public boolean equals(Object other) {
        if(this==other) return true;
        if(!(other instanceof RouteKey)) return false;
        RouteKey k=(RouteKey)other;
        return channel==k.channel && resource==k.resource && owner.equals(k.owner);
    }
    @Override public int hashCode() { return Objects.hash(owner,channel,resource); }
}
