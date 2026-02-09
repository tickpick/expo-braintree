import { ConfigPlugin } from "expo/config-plugins";
interface BraintreePluginProps {
    merchantIdentifier?: string;
    urlScheme?: string;
    enableVenmo?: boolean;
    enablePayPal?: boolean;
    enableGooglePay?: boolean;
}
declare const withBraintree: ConfigPlugin<BraintreePluginProps | void>;
export default withBraintree;
