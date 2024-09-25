import logist.topology.Topology;

import java.util.ArrayList;
import java.util.Objects;

public class State {
    private Topology.City city;
    private Topology.City destination;
    private ArrayList<ActionClass> possibleAction = new ArrayList<>();

    public State(Topology.City city, Topology.City destination) {
        this.city = city;
        this.destination = destination;
        generatePossibleActions();
    }

    public void generatePossibleActions() {
        for (Topology.City neighbor : this.city) {
            this.possibleAction.add(new ActionClass(neighbor, false));
        }
        if (destination != null) {
            this.possibleAction.add(new ActionClass(destination, true));
        }
    }

    public ArrayList<ActionClass> getPossibleActions() {
        return possibleAction;
    }

    public Topology.City getCity() {
        return city;
    }

    public Topology.City getDestination() {
        return destination; // null if no delivery available
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        State myObject = (State) obj;
        return this.city.equals(myObject.getCity()) && this.destination.equals(myObject.getDestination());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.city, this.destination);
    }
}
