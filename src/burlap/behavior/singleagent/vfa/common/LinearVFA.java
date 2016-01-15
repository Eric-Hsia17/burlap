package burlap.behavior.singleagent.vfa.common;

import burlap.behavior.policy.Policy;
import burlap.behavior.policy.RandomPolicy;
import burlap.behavior.singleagent.EpisodeAnalysis;
import burlap.behavior.singleagent.vfa.*;
import burlap.behavior.singleagent.vfa.cmac.CMACFeatureDatabase;
import burlap.debugtools.MyTimer;
import burlap.domain.singleagent.lunarlander.LunarLanderDomain;
import burlap.domain.singleagent.lunarlander.LunarLanderRF;
import burlap.domain.singleagent.lunarlander.LunarLanderTF;
import burlap.oomdp.auxiliary.common.NullTermination;
import burlap.oomdp.core.Domain;
import burlap.oomdp.core.TerminalFunction;
import burlap.oomdp.core.states.State;
import burlap.oomdp.singleagent.Action;
import burlap.oomdp.singleagent.GroundedAction;
import burlap.oomdp.singleagent.RewardFunction;
import burlap.oomdp.singleagent.common.NullRewardFunction;

import java.util.*;


/**
 * This class is used for general purpose linear VFA. It only needs to be provided a FeatureDatabase object that will be used to store
 * retrieve state features. For every feature returned by the feature database, this class will automatically create a weight associated with it.
 * The returned approximated value for any state is the linear combination of state features and weights.
 *  
 * @author James MacGlashan
 *
 */
public class LinearVFA implements ValueFunctionApproximation {

	/**
	 * A feature database for which a unique function weight will be associated
	 */
	protected FeatureDatabase						featureDatabase;
	
	/**
	 * A map from feature identifiers to function weights
	 */
	protected Map<Integer, FunctionWeight>			weights;
	
	/**
	 * A default weight for the functions
	 */
	protected double								defaultWeight = 0.0;
	
	
	/**
	 * Initializes with a feature database; the default weight value will be zero
	 * @param featureDatabase the feature database to use
	 */
	public LinearVFA(FeatureDatabase featureDatabase) {
		
		this.featureDatabase = featureDatabase;
		if(featureDatabase.numberOfFeatures() > 0){
			this.weights = new HashMap<Integer, FunctionWeight>(featureDatabase.numberOfFeatures());
		}
		else{
			this.weights = new HashMap<Integer, FunctionWeight>();
		}
		
	}
	
	
	/**
	 * Initializes
	 * @param featureDatabase the feature database to use
	 * @param defaultWeight the default feature weight to initialize feature weights to
	 */
	public LinearVFA(FeatureDatabase featureDatabase, double defaultWeight) {
		
		this.featureDatabase = featureDatabase;
		this.defaultWeight = defaultWeight;
		if(featureDatabase.numberOfFeatures() > 0){
			this.weights = new HashMap<Integer, FunctionWeight>(featureDatabase.numberOfFeatures());
		}
		else{
			this.weights = new HashMap<Integer, FunctionWeight>();
		}
		
	}

	@Override
	public ApproximationResult getStateValue(State s) {
		
		List <StateFeature> features = featureDatabase.getStateFeatures(s);
		return this.getApproximationResultFrom(features);
	}

	@Override
	public List<ActionApproximationResult> getStateActionValues(State s, List<GroundedAction> gas) {
	
		List <ActionFeaturesQuery> featureSets = this.featureDatabase.getActionFeaturesSets(s, gas);
		List <ActionApproximationResult> results = new ArrayList<ActionApproximationResult>(featureSets.size());
		
		for(ActionFeaturesQuery afq : featureSets){
			
			ApproximationResult r = this.getApproximationResultFrom(afq.features);
			ActionApproximationResult aar = new ActionApproximationResult(afq.queryAction, r);
			results.add(aar);
			
		}
		
		return results;
	}

	@Override
	public WeightGradient getWeightGradient(ApproximationResult approximationResult) {
		
		WeightGradient gradient = new WeightGradient(approximationResult.stateFeatures.size());
		for(StateFeature sf : approximationResult.stateFeatures){
			gradient.put(sf.id, sf.value);
		}
		
		return gradient;
	}
	
	
	
	/**
	 * Computes the linear function over the given features and the stored feature weights.
	 * @param features List of the {@link StateFeature} obejcts defining the state features of this approximator.
	 * @return the linear function over the given features and the stored feature weights stored in a {@link ApproximationResult}.
	 */
	protected ApproximationResult getApproximationResultFrom(List <StateFeature> features){
		
		List <FunctionWeight> activedWeights = new ArrayList<FunctionWeight>(features.size());
		
		double predictedValue = 0.;
		for(StateFeature sf : features){
			FunctionWeight fw = this.weights.get(sf.id);
			if(fw == null){
				fw = new FunctionWeight(sf.id, defaultWeight);
				this.weights.put(fw.weightId(), fw);
			}
			predictedValue += sf.value*fw.weightValue();
			activedWeights.add(fw);
		}
		
		ApproximationResult result = new ApproximationResult(predictedValue, features, activedWeights);
		
		return result;
		
	}
	
	
	@Override
	public void resetWeights(){
		this.weights.clear();
	}


	@Override
	public void setWeight(int featureId, double w) {
		FunctionWeight fw = this.weights.get(featureId);
		if(fw == null){
			fw = new FunctionWeight(featureId, w);
			this.weights.put(featureId, fw);
		}
		else{
			fw.setWeight(w);
		}
	}


	@Override
	public int numFeatures() {
		return this.featureDatabase.numberOfFeatures();
	}


	@Override
	public FunctionWeight getFunctionWeight(int featureId) {
		return this.weights.get(featureId);
	}

	@Override
	public LinearVFA copy() {

		LinearVFA vfa = new LinearVFA(this.featureDatabase.copy(), this.defaultWeight);
		vfa.weights = new HashMap<Integer, FunctionWeight>(this.weights.size());
		for(Map.Entry<Integer, FunctionWeight> e : this.weights.entrySet()){
			FunctionWeight fw = e.getValue();
			vfa.weights.put(e.getKey(), new FunctionWeight(fw.weightId(), fw.weightValue()));
		}

		return vfa;
	}

	public static void main(String[] args) {

		LunarLanderDomain lld = new LunarLanderDomain();
		Domain domain = lld.generateDomain();
		RewardFunction rf = new LunarLanderRF(domain);
		TerminalFunction tf = new LunarLanderTF(domain);

		State s = LunarLanderDomain.getCleanState(domain, 0);
		LunarLanderDomain.setAgent(s, 0., 5., 0.);
		LunarLanderDomain.setPad(s, 75., 95., 0., 10.);

		int nTilings = 5;
		CMACFeatureDatabase cmac = new CMACFeatureDatabase(nTilings,
				CMACFeatureDatabase.TilingArrangement.RANDOMJITTER);
		double resolution = 10.;

		double angleWidth = 2 * lld.getAngmax() / resolution;
		double xWidth = (lld.getXmax() - lld.getXmin()) / resolution;
		double yWidth = (lld.getYmax() - lld.getYmin()) / resolution;
		double velocityWidth = 2 * lld.getVmax() / resolution;

		cmac.addSpecificationForAllTilings(LunarLanderDomain.AGENTCLASS,
				domain.getAttribute(LunarLanderDomain.AATTNAME),
				angleWidth);
		cmac.addSpecificationForAllTilings(LunarLanderDomain.AGENTCLASS,
				domain.getAttribute(LunarLanderDomain.XATTNAME),
				xWidth);
		cmac.addSpecificationForAllTilings(LunarLanderDomain.AGENTCLASS,
				domain.getAttribute(LunarLanderDomain.YATTNAME),
				yWidth);
		cmac.addSpecificationForAllTilings(LunarLanderDomain.AGENTCLASS,
				domain.getAttribute(LunarLanderDomain.VXATTNAME),
				velocityWidth);
		cmac.addSpecificationForAllTilings(LunarLanderDomain.AGENTCLASS,
				domain.getAttribute(LunarLanderDomain.VYATTNAME),
				velocityWidth);


		double defaultQ = 0.5;
		ValueFunctionApproximation vfa = cmac.generateVFA(defaultQ/nTilings);


		Policy p = new RandomPolicy(domain);
		int trajectories = 500;
		List<EpisodeAnalysis> episodes = new ArrayList<EpisodeAnalysis>(trajectories);
		for(int i = 0; i < trajectories; i++){
			episodes.add(p.evaluateBehavior(s, new NullRewardFunction(), new NullTermination(), 2000));
		}

		List<GroundedAction> actions = Action.getAllApplicableGroundedActionsFromActionList(domain.getActions(), s);

		System.out.println("timing vfa");
		MyTimer timer = new MyTimer(true);
		int i = 0;
		for(EpisodeAnalysis ea : episodes){
			System.out.println("episode: " + i);
			for(int t = 0; t < ea.maxTimeStep(); t++){
				List<ActionApproximationResult> res = vfa.getStateActionValues(ea.getState(t), actions);
				vfa.getWeightGradient(res.get(0).approximationResult);

			}
			i++;
		}
		timer.stop();
		System.out.println("time: " + timer.getTime());


	}
}
