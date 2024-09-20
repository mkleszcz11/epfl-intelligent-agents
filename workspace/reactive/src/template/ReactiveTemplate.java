package template;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

public class ReactiveTemplate implements ReactiveBehavior {

	private Random random;
	private double pPickup;
	private int numActions;
	private Agent myAgent;
	private double discountFactor = 0.95; // Gamma (γ)

	// Transition and reward tables
	private Map<City, Map<City, Double>> rewardTable;
	private Map<City, Map<City, City>> transitionTable;

	// Value function V(s)
	private Map<City, Double> valueFunction;

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {

		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);

		this.random = new Random();
		this.pPickup = discount;
		this.numActions = 0;
		this.myAgent = agent;

		// Initialize reward and transition tables
		rewardTable = new HashMap<>();
		transitionTable = new HashMap<>();
		valueFunction = new HashMap<>();

		// Initialize states, actions, and values
		for (City city : topology.cities()) {
			rewardTable.put(city, new HashMap<>());
			transitionTable.put(city, new HashMap<>());
			valueFunction.put(city, 0.0); // Arbitrarily initialize V(s)

			for (City neighbor : city.neighbors()) {
				double reward = td.reward(city, neighbor); // Get reward for the task
				rewardTable.get(city).put(neighbor, reward);
				transitionTable.get(city).put(neighbor, neighbor); // Transition to the neighbor city
			}
		}

		// Run value iteration to compute optimal values
		runValueIteration(topology.cities());
	}

	// The value iteration algorithm to calculate the optimal policy
	private void runValueIteration(List<City> cities) {
		boolean isConverged;
		double epsilon = 0.01; // Threshold for convergence

		do {
			isConverged = true;

			// Loop over all states (cities)
			for (City city : cities) {
				double oldValue = valueFunction.get(city);

				// Loop over all actions (moving to neighbors)
				double maxQValue = Double.NEGATIVE_INFINITY;
				for (City neighbor : city.neighbors()) {
					double reward = rewardTable.get(city).get(neighbor);
					double neighborValue = valueFunction.get(neighbor);
					double qValue = reward + discountFactor * neighborValue;

					if (qValue > maxQValue) {
						maxQValue = qValue;
					}
				}

				// Update V(s) with the best Q(s, a)
				valueFunction.put(city, maxQValue);

				// Check if the value has converged (small enough change)
				if (Math.abs(oldValue - maxQValue) > epsilon) {
					isConverged = false;
				}
			}
		} while (!isConverged);
	}


	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;
		City currentCity = vehicle.getCurrentCity();

		// If a task is available and we decide to pick it up
		if (availableTask != null && random.nextDouble() <= pPickup) {
			action = new Pickup(availableTask);
		} else {
			// Otherwise, move to the best neighboring city according to the value function
			City bestNeighbor = null;
			double maxQValue = Double.NEGATIVE_INFINITY;

			// Find the best action (city to move to) based on value function
			for (City neighbor : currentCity.neighbors()) {
				double reward = rewardTable.get(currentCity).get(neighbor);
				double neighborValue = valueFunction.get(neighbor);
				double qValue = reward + discountFactor * neighborValue;

				if (qValue > maxQValue) {
					maxQValue = qValue;
					bestNeighbor = neighbor;
				}
			}

			// Move to the best neighboring city
			if (bestNeighbor != null) {
				action = new Move(bestNeighbor);
			} else {
				// Fallback to a random move if no best action is found (shouldn't happen)
				action = new Move(currentCity.randomNeighbor(random));
			}
		}

		// Log the agent's performance after each action
		if (numActions >= 1) {
			System.out.println("The total profit after " + numActions + " actions is " + myAgent.getTotalProfit()
					+ " (average profit: " + (myAgent.getTotalProfit() / (double) numActions) + ")");
		}
		numActions++;

		return action;
	}
}