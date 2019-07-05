package de.thegerman.dextrader.repositories

import de.thegerman.dextrader.data.AssetApi
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.hexAsBigIntegerOrNull
import java.math.BigInteger

interface AssetRepository {
    suspend fun loadAssets(owner: Solidity.Address): List<Asset>

    data class Asset(val contract: Solidity.Address, val contractName: String?, val id: BigInteger, val name: String?, val image: String?)
}

class AssetRepositoryImpl(
    private val assetApi: AssetApi
) : AssetRepository {
    override suspend fun loadAssets(owner: Solidity.Address): List<AssetRepository.Asset> =
        assetApi.assets(owner.asEthereumAddressChecksumString()).assets.mapNotNull {
            val contract = it.contract.address.asEthereumAddress() ?: return@mapNotNull null
            val tokenId = it.tokenId.hexAsBigIntegerOrNull() ?: return@mapNotNull null
            AssetRepository.Asset(contract, it.contract.name, tokenId, it.name, it.imagePreviewUrl ?: it.imageUrl)
        }
}