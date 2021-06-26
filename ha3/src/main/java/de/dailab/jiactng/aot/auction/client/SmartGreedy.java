package de.dailab.jiactng.aot.auction.client;
import de.dailab.jiactng.aot.auction.onto.*;

import java.util.*;
import java.util.Map.Entry;


/*
* params: Wallet
* calculateBid(CallForBids)
* returns Double else -1
*/
public class SmartGreedy {
    private final Wallet myWallet;
    private final Map<Resource[], Integer> bundlesMap;

    public SmartGreedy(Wallet wallet) {
        this.myWallet = wallet;
        this.bundlesMap = initBundles();
    }

    // returns minOffer price, for now
    public double calculateBuyBid(CallForBids cfb) {
        List<Resource> bundle = cfb.getBundle();
        double minOffer = cfb.getMinOffer();
        double brutto = this.calculateBrutto(bundle);
        if (brutto > minOffer) {
            return minOffer + 150; /////////////////////////////////////////////// changed here
        } else {
            return -1;
        }
    }

    // Calculate added value to wallet from bundle
    private double calculateBrutto(List<Resource> bundle) {
        double walletBefore = this.calculateWalletValue(this.myWallet);
        //this.myWallet.add(bundle);
        double walletAfter = this.calculateWalletValue(this.myWallet);
        //return walletAfter - walletBefore;
        return 100;
    }

    // while wallet resources not empty, remove bundle and aggregate price
    private double calculateWalletValue(Wallet wallet)  {
        // Note: operations on wallet are pass-by-value
        int totalValue = 0;

        // iterate thru bundles in BundlesMap
        for(Resource[] bundle: this.bundlesMap.keySet()){

            List<Resource> listBundle = Arrays.asList(bundle);
            // if wallet contains bundle, add price to total value & remove bundle from wallet
            if (wallet.contains(listBundle)) {
                totalValue += this.bundlesMap.get(bundle);
                wallet.remove(listBundle);
            }
        }
        return (double) totalValue;
    }

    // initialize BundlesMap, sorted in descending price order
    private Map<Resource[], Integer> initBundles() {
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
        // sort bundles map by price, descending
        return sortByValue(unsortedBundles);
    }

    // sort Map by price in descending order
    private static Map<Resource[], Integer> sortByValue(Map<Resource[], Integer> map){
        // get entry set
        List<Entry<Resource[], Integer>> list = new ArrayList<>(map.entrySet());

        // sort entry set by value, reverse order
        list.sort(Entry.comparingByValue(Comparator.reverseOrder()));

        // LinkedHashMap preserves iteration order based on insertion order!
        Map<Resource[], Integer> result = new LinkedHashMap<>();

        // put entries in new map, in sorted list order
        for (Entry<Resource[], Integer> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    // if wallet contains bundle, return true
    public boolean calculateSellBid(CallForBids cfb) {
        List<Resource> bundle = cfb.getBundle();
        //System.out.println("Wallet: " + this.myWallet.toString() + " contains bundle: " + bundle.toString() + " , so sell!!");
        if (this.myWallet.contains(bundle)){
            System.out.println("Wallet contains bundle, so sell!!");
            return true;
        }
        else {
            return false;
        }
    }
}
