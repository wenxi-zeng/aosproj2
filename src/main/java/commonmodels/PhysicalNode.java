package commonmodels;

import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "id",
        "address",
        "port",
        "active"
})
public class PhysicalNode implements Serializable
{

    @JsonProperty("id")
    private String id;
    @JsonProperty("address")
    private String address;
    @JsonProperty("port")
    private int port;
    @JsonProperty("active")
    private boolean active = true;
    private final static long serialVersionUID = -4955842269298436005L;

    /**
     * No args constructor for use in serialization
     *
     */
    public PhysicalNode() {
    }

    /**
     *
     * @param address
     * @param port
     * @param active
     * @param id
     */
    public PhysicalNode(String id, String address, int port, boolean active) {
        super();
        this.id = id;
        this.address = address;
        this.port = port;
        this.active = active;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    public PhysicalNode withId(String id) {
        this.id = id;
        return this;
    }

    @JsonProperty("address")
    public String getAddress() {
        return address;
    }

    @JsonProperty("address")
    public void setAddress(String address) {
        this.address = address;
    }

    public PhysicalNode withAddress(String address) {
        this.address = address;
        return this;
    }

    @JsonProperty("port")
    public int getPort() {
        return port;
    }

    @JsonProperty("port")
    public void setPort(int port) {
        this.port = port;
    }

    public PhysicalNode withPort(int port) {
        this.port = port;
        return this;
    }

    @JsonProperty("active")
    public boolean isActive() {
        return active;
    }

    @JsonProperty("active")
    public void setActive(boolean active) {
        this.active = active;
    }

    public PhysicalNode withActive(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("id", id).append("address", address).append("port", port).append("active", active).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(active).append(id).append(address).append(port).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof PhysicalNode) == false) {
            return false;
        }
        PhysicalNode rhs = ((PhysicalNode) other);
        return new EqualsBuilder().append(active, rhs.active).append(id, rhs.id).append(address, rhs.address).append(port, rhs.port).isEquals();
    }

}