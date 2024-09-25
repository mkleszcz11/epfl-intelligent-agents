import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.task.TaskDistribution;
import logist.topology.Topology;

import java.util.ArrayList;

public class RewardMatrix {
    ArrayList<State> stateSpace;
    TaskDistribution taskDistribution;
    Agent agent;

    public RewardMatrix(ArrayList<State> stateSpace, TaskDistribution taskDistribution, Agent agent) {
        this.stateSpace = stateSpace;
        this.taskDistribution = taskDistribution;
        this.agent = agent;
    }

    public double getReward(State state, ActionClass actionClass) {
        double reward = - getAvgPathCost(state.getCity(), actionClass.getMove());
        if (actionClass.isDelivery()) {
            reward += taskDistribution.reward(state.getCity(), actionClass.getMove());
        }
        return reward;
    }

    private double getAvgPathCost(Topology.City start, Topology.City stop) {
        double cost = 0.0;
        int i = 0;
        for (Vehicle vehicle : agent.vehicles()) {
            i++;
            cost += start.distanceTo(stop) * vehicle.costPerKm();
        }
        cost = cost / i;
        return cost;
    }
}
