'use strict';

const ip4ToInt = ip =>
    ip.split('.').reduce((int, oct) => (int << 8) + parseInt(oct, 10), 0) >>> 0;

const isIp4InCidr = ip => cidr => {
    const [range, bits = 32] = cidr.split('/');
    const mask = ~(2 ** (32 - bits) - 1);
    return mask === 0
        ? ip4ToInt(ip) === ip4ToInt(range)
        : (ip4ToInt(ip) & mask) === (ip4ToInt(range) & mask);
};

const isIp4InCidrs = (ip, cidrs) => cidrs.some(isIp4InCidr(ip));

exports.handler = async(event) => {
    if (process.env.ENVIRONMENT === 'build') {
        return {
            'isAuthorized': true
        };
    }
    const ipAddress = event.requestContext.http.sourceIp;
    const validIps = [
        '217.196.229.77/32',
        '217.196.229.79/32',
        '217.196.229.80/31',
        '51.149.8.0/25',
        '51.149.8.128/29',
        '213.86.153.211/32',
        '213.86.153.212/31',
        '213.86.153.214/32',
        '213.86.153.235/32',
        '213.86.153.236/31',
        '213.86.153.231/32',
        '3.9.227.33/32',
        '18.132.149.145/32',
        '51.142.180.30/32',
        '185.120.72.241/32',
        '185.120.72.242/31',
        '3.9.56.99/32',
        //Below IP's are public IP of AWS Codebuild in eu-west-2 region
        '35.176.92.32/29',
        '18.169.230.200/29'
    ];
    const isValidIp = isIp4InCidrs(ipAddress, validIps);
    return {
        'isAuthorized': isValidIp
    };
}