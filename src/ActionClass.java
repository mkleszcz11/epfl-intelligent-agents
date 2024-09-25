import logist.topology.Topology;
import java.util.Objects;

public class ActionClass {
    private final Topology.City move;
    private final Boolean delivery;

    public ActionClass(Topology.City move, Boolean delivery) {
        this.move = move;
        this.delivery = delivery;
    }

    public Topology.City getMove() {
        return move;
    }

    public Boolean isDelivery() {
        return delivery;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ActionClass myObject = (ActionClass) obj;
        return this.delivery.equals(myObject.isDelivery()) && this.move.equals(myObject.getMove());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.move, this.delivery);
    }
}
