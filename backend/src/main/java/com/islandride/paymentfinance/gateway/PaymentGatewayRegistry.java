package com.islandride.paymentfinance.gateway;

import com.islandride.core.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentGatewayRegistry {

    private final PaymentGatewayProperties properties;
    private final Map<String, PaymentGatewayAdapter> adapters;

    public PaymentGatewayRegistry(PaymentGatewayProperties properties,
                                  List<PaymentGatewayAdapter> adapterList) {
        this.properties = properties;
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(
                        adapter -> adapter.providerCode().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (a, b) -> a
                ));
    }

    public PaymentGatewayAdapter getConfiguredAdapter() {
        String key = properties.getProvider() != null
                ? properties.getProvider().trim().toLowerCase(Locale.ROOT)
                : MockGatewayAdapter.PROVIDER_CODE;
        PaymentGatewayAdapter adapter = adapters.get(key);
        if (adapter == null) {
            adapter = adapters.get(RazorpayGatewayAdapter.PROVIDER_CODE);
        }
        if (adapter == null) {
            throw new BusinessRuleException("No payment gateway adapter is registered for provider: " + key);
        }
        return adapter;
    }

    public PaymentGatewayAdapter getAdapter(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return getConfiguredAdapter();
        }
        PaymentGatewayAdapter adapter = adapters.get(providerCode.trim().toLowerCase(Locale.ROOT));
        if (adapter == null) {
            throw new BusinessRuleException("Unsupported payment gateway provider: " + providerCode);
        }
        return adapter;
    }
}

