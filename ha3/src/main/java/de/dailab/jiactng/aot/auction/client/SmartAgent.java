package de.dailab.jiactng.aot.auction.client;

import de.dailab.jiactng.aot.auction.onto.CallForBids;
import de.dailab.jiactng.aot.auction.onto.Resource;
import de.dailab.jiactng.aot.auction.onto.Wallet;

import java.util.*;
import java.util.Map.Entry;


/*
* params: Wallet
* calculateBid(CallForBids)
* returns Double else -1
*/
public class SmartAgent {
    private final Wallet myWallet;
    private final Map<Resource[], Integer> bundlesMap;
    private final List<Resource> resourceList;
    private final Map<Resource,Double> resourceValues;
    private final Integer ROUNDS = 100;

    public SmartAgent(Wallet wallet) {
        this.myWallet = wallet;
        Resource[] resourcesArray = {Resource.A, Resource.B, Resource.C, Resource.D, Resource.E, Resource.F, Resource.J, Resource.K, Resource.G};
        this.resourceList = Arrays.asList(resourcesArray);
        this.bundlesMap = initBundles(ROUNDS);
        this.resourceValues = calculateEveryResourceValue();
    }


    public double calculateBuyBid(CallForBids cfb) {
        double minOffer = cfb.getMinOffer();
        double buyOffer = 0.0;
        List<Resource> bundle = cfb.getBundle();
        for (Resource res: bundle) {
            buyOffer += this.resourceValues.get(res);
        }
        System.out.println("----------------------BUYOFFER:"+buyOffer +"------------------------");
        if (buyOffer<minOffer) return -1;
        return buyOffer;
    }

    private Map<Resource[], Integer>  estimateFutureValue(Map<Resource[], Integer> bundlesMap, int n){
        Integer calculatedBonus = 5*n;
        for (Resource[] key: bundlesMap.keySet()) {
            int valueBefore = bundlesMap.get(key);
            valueBefore += calculatedBonus;
            bundlesMap.put(key,valueBefore);
        };
        return bundlesMap;
    }
    //calculates the maximum value of a single resource
    private double calculateMaxResourceValue(Resource res){
        double estimatedValue = 0;
        for (Resource[] key: this.bundlesMap.keySet()) {
            if (Arrays.asList(key).contains(res)){
                int bundlePrice = this.bundlesMap.get(key);
                double letterValue = bundlePrice/ key.length;
                if (estimatedValue<letterValue){
                    estimatedValue=letterValue;
                }
            }
        } return estimatedValue;
    }

    private Map<Resource, Double> calculateEveryResourceValue(){
        Map<Resource, Double> resourceValues = new HashMap<>();
        for (Resource res: this.resourceList) {
            if (res == Resource.G) {
                resourceValues.put(Resource.G,0.0);
            }
            else{
                resourceValues.put(res,calculateMaxResourceValue(res));
            }
        }
        return resourceValues;
    }

    // initialize BundlesMap, sorted in descending price order
    private Map<Resource[], Integer> initBundles(int n) {
        Resource[][] bundles = {{Resource.A, Resource.A}, {Resource.A, Resource.A, Resource.A},
                {Resource.A, Resource.A, Resource.A, Resource.A}, {Resource.A, Resource.A, Resource.B},
                {Resource.A, Resource.J, Resource.K}, {Resource.B, Resource.B},
                {Resource.C, Resource.C, Resource.C, Resource.D, Resource.D, Resource.D},
                {Resource.C, Resource.C, Resource.D, Resource.D, Resource.A, Resource.A},
                {Resource.C, Resource.C, Resource.D, Resource.D, Resource.B, Resource.B},
                {Resource.E, Resource.E, Resource.E, Resource.E, Resource.E, Resource.F},
                {Resource.E, Resource.E, Resource.E, Resource.E, Resource.F},
                {Resource.E, Resource.E, Resource.E, Resource.F},
                {Resource.E, Resource.E, Resource.F},
                {Resource.F, Resource.F}, {Resource.F, Resource.J, Resource.K},
                {Resource.A, Resource.B, Resource.C, Resource.D, Resource.E, Resource.F, Resource.J, Resource.K}};
        Integer[] prices = {200, 300, 400, 200, 200, 50, 1200, 800, 600, 1600, 800, 400, 200, 100, 300, 1400};
        Map<Resource[], Integer> unsortedBundles = new HashMap<>();
        // add <bundle,price> to map, random order
        for (int i=0; i<bundles.length; i++){
            unsortedBundles.put(bundles[i], prices[i]);
        }
        unsortedBundles = estimateFutureValue(unsortedBundles, n);
        // sort bundles map by price, descending
        return unsortedBundles;
    }
    // if wallet contains bundle, return true
    public boolean calculateSellBid(CallForBids cfb) {
        List<Resource> bundle = cfb.getBundle();
        if (this.myWallet.contains(bundle)) {
            return true;
        } else {
            return false;
        }
    }
}
