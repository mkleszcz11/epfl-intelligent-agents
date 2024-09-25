import logist.task.TaskDistribution;
import logist.topology.Topology;

import java.util.ArrayList;
import java.util.HashMap;

public class TransitionMatrix {
    HashMap<ActionClass, HashMap<State, Double>> matrix = new HashMap<>();
    ArrayList<State> stateSpace;
    TaskDistribution taskDistribution;
    Topology topology;

    public TransitionMatrix(ArrayList<State> stateSpace, TaskDistribution taskDistribution, Topology topology) {
        this.stateSpace = stateSpace;
        this.taskDistribution = taskDistribution;
        this.topology = topology;
    }

    public void generateTransitionMatrix() {
        for (Topology.City city : this.topology) {
            ActionClass a1 = new ActionClass(city, false);
            ActionClass a2 = new ActionClass(city, true);
            HashMap<State, Double> probabilityDistribution = new HashMap<>();
            for (State state : stateSpace) { // s' in the transition matrix
                if (state.getCity().equals(city)) {
                    probabilityDistribution.put(state, taskDistribution.probability(city, state.getDestination()));
                }
            }
            matrix.put(a1, probabilityDistribution);
            matrix.put(a2, probabilityDistribution);
        }

        /*
        for (State state1 : stateSpace) {
            ActionClass a1 = new ActionClass(state1.getCity(), false);
            ActionClass a2 = new ActionClass(state1.getCity(), true);
            HashMap<State, Double> probabilityDistribution = new HashMap<>();
            //double probabilitySum = 0.0;
            Topology.City actionDestination = state1.getCity();
            for (State state2 : stateSpace) {
                if (!state2.getCity().equals(actionDestination)) {
                    double prob = taskDistribution.probability(actionDestination, state2.getCity());
                    probabilityDistribution.put(state2, prob);
                    probabilitySum += prob;
                } else {
                    double prob = taskDistribution.probability(actionDestination, null);
                    probabilityDistribution.put(state2, prob);
                    probabilitySum += prob;
                }
            }
            for (State state2 : probabilityDistribution.keySet()) {
                probabilityDistribution.put(state2, probabilityDistribution.get(state2) / probabilitySum);
            }
            matrix.put(a1, probabilityDistribution);
            matrix.put(a2, probabilityDistribution);
        }
        */
    }

    public HashMap<State, Double> getTransition(ActionClass actionClass) {
        return matrix.get(actionClass);
    }
}

/*
public class TransitionMatrix {
    HashMap<ActionClass, HashMap<State, Double>> matrix = new HashMap<>();
    ArrayList<State> stateSpace;
    TaskDistribution taskDistribution;

    public TransitionMatrix(ArrayList<State> stateSpace, TaskDistribution taskDistribution) {
        this.stateSpace = stateSpace;
        this.taskDistribution = taskDistribution;
    }

    public void generateTransitionMatrix() {
        for (State state1 : stateSpace) {
            Topology.City currentCity = state1.getCity();
            Topology.City destination = state1.getDestination(); // destination can be null

            // Generate probability distribution for moving to neighboring cities
            for (Topology.City neighbor : currentCity.neighbors()) {
                ActionClass moveAction = new ActionClass(neighbor, false); // Action to move to neighbor
                HashMap<State, Double> moveDistribution = generateNeighborTransition(currentCity, neighbor);
                matrix.put(moveAction, moveDistribution);
            }

            // Generate probability distribution for delivering the package
            if (destination != null) {
                ActionClass deliveryAction = new ActionClass(destination, true); // Action to deliver
                HashMap<State, Double> deliverDistribution = generateDeliveryTransition(currentCity, destination);
                matrix.put(deliveryAction, deliverDistribution);
            }
        }
    }

    // Generates transition probabilities for moving to neighboring cities
    private HashMap<State, Double> generateNeighborTransition(Topology.City current, Topology.City neighbor) {
        HashMap<State, Double> probabilityDistribution = new HashMap<>();
        double probabilitySum = 0.0;

        for (State state2 : stateSpace) {
            if (!state2.getCity().equals(current)) {
                // Task probability from current city to the neighboring city
                double prob = taskDistribution.probability(current, state2.getCity());
                probabilityDistribution.put(state2, prob);
                probabilitySum += prob;
            } else {
                // No task case
                double prob = taskDistribution.probability(current, null);
                probabilityDistribution.put(state2, prob);
                probabilitySum += prob;
            }
        }

        // Normalize probabilities
        for (State state2 : probabilityDistribution.keySet()) {
            probabilityDistribution.put(state2, probabilityDistribution.get(state2) / probabilitySum);
        }

        return probabilityDistribution;
    }

    // Generates transition probabilities for delivering a package
    private HashMap<State, Double> generateDeliveryTransition(Topology.City current, Topology.City destination) {
        HashMap<State, Double> probabilityDistribution = new HashMap<>();
        double probabilitySum = 0.0;

        // We are now in the destination city after delivering the task
        for (State state2 : stateSpace) {
            // If state2 represents the destination city
            if (state2.getCity().equals(destination)) {
                if (state2.getDestination() == null) {
                    // No package case, agent can move to neighboring cities or wait
                    double probNoTask = taskDistribution.probability(destination, null); // Probability of no task
                    probabilityDistribution.put(state2, probNoTask);
                    probabilitySum += probNoTask;
                } else {
                    // Probability that a new task is available to some other city
                    double probNewTask = taskDistribution.probability(destination, state2.getDestination());
                    probabilityDistribution.put(state2, probNewTask);
                    probabilitySum += probNewTask;
                }
            }
        }

        // Normalize probabilities so they sum up to 1
        for (State state2 : probabilityDistribution.keySet()) {
            probabilityDistribution.put(state2, probabilityDistribution.get(state2) / probabilitySum);
        }

        return probabilityDistribution;
    }

    // Retrieve transition probabilities for a given action
    public HashMap<State, Double> getTransition(ActionClass actionClass) {
        return matrix.get(actionClass);
    }
}
*/
