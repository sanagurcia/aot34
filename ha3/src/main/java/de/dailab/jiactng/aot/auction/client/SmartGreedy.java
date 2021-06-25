package de.dailab.jiactng.aot.auction.client;
import de.dailab.jiactng.aot.auction.onto.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/*
* params: Wallet
* calculateBid(CallForBids)
* returns Double else -1
*/
public class SmartGreedy {
    private Wallet myWallet;
    private Map<String, Integer> bundlePrices;

    public SmartGreedy(Wallet wallet) {
        this.myWallet = wallet;
        this.initBundles();
    }

    public double calculateBid(CallForBids cfb) {
        List<Resource> bundle = cfb.getBundle();
        double minOffer = cfb.getMinOffer();
        double brutto = this.calculateBrutto(bundle);
        return -1;
    }

    // Calculate added value to wallet from bundle
    private double calculateBrutto(List<Resource> bundle) {
        double walletBefore = this.calculateWalletValue(this.myWallet);
        // Wallet dummyWallet = this.myWallet.clone();
        double walletAfter = this.calculateWalletValue(dummyWallet);
        return walletAfter - walletBefore;
    }

    private double calculateWalletValue(Wallet myWallet) {

    }

    private double calculateProfit(List<Resource> bundle) {
    }

    private void initBundles() {
        String[] bundles = {"AA", "AAA", "AAAA", "AAB", "AJK", "BB", "CCCDDD",
            "CCDDAA", "CCDDBB", "EEEEEF", "EEEEF", "EEEF", "EEF", "FF", "FJK", "ABCDEFJK"};
        Integer[] prices = {200, 300, 400, 200, 200, 50, 1200, 800, 600, 1600, 800, 400, 200, 100, 300, 1400};
        this.bundlePrices = new HashMap<>();
        for (int i=0; i<bundles.length; i++){
            this.bundlePrices.put(bundles[i], prices[i]);
        }
    }

}
